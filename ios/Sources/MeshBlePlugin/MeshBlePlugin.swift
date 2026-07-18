import Foundation
import UIKit
import Capacitor
import CoreBluetooth

@objc(MeshBlePlugin)
public class MeshBlePlugin: CAPPlugin, CAPBridgedPlugin, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
    public let identifier = "MeshBlePlugin"
    public let jsName = "MeshBle"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "broadcast", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "send", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setKeepaliveFrame", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getStatus", returnType: CAPPluginReturnPromise)
    ]

    private let eventFrame = "frame"
    private let eventStatus = "status"
    private let broadcastPeer = "*"
    private let frameCharacteristicUUID = CBUUID(string: "29B8D9F3-2C2B-4ED1-A12C-7401E5B7B37F")

    private let chunkMagic: UInt8 = 0x4d
    private let chunkVersion: UInt8 = 0x01
    private let chunkHeaderBytes = 8
    private let maxChunkPayload = 160
    private let maxChunks = 255
    private var maxEnvelopeBytes = 8192
    private var initialHops = 0
    private var maxSeenIds = 512
    private var reassemblyTtl: TimeInterval = 30

    // Every CoreBluetooth callback AND every Capacitor bridge call runs on this one
    // serial queue, so the shared mutable state (writeQueues, peripherals, reassembly,
    // counters…) is never touched from two threads at once. Previously CB ran on the
    // main thread (queue: nil) while bridge send/broadcast ran on a background queue —
    // a data race that corrupted a Swift COW buffer mid-handshake and crashed the app
    // (EXC_BAD_ACCESS in swift_isUniquelyReferenced_nonNull_native).
    private let bleQueue = DispatchQueue(label: "dev.forgesworn.meshble")

    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var serviceUUID: CBUUID?
    private var frameCharacteristic: CBMutableCharacteristic?

    private var room: String?
    private var selfId: String?
    private var running = false
    private var advertisingActive = false
    private var scanningActive = false
    private var pendingStartCall: CAPPluginCall?
    private var txFrames = 0
    private var txChunks = 0
    private var rxFrames = 0
    private var rxChunks = 0
    private var droppedFrames = 0
    private var relayedFrames = 0
    private var lastError: String?

    private var peripherals: [UUID: CBPeripheral] = [:]
    private var writableCharacteristics: [UUID: CBCharacteristic] = [:]
    private var writeQueues: [UUID: [Data]] = [:]
    private var writingPeripherals = Set<UUID>()

    private var inbound: [String: Reassembly] = [:]
    private var peerPeripheralIds: [String: UUID] = [:]
    private var seenIds = [String]()
    private var seenIdSet = Set<String>()

    // --- Background presence keepalive -------------------------------------
    // WKWebView suspends ALL JavaScript when the app backgrounds, so the JS
    // engine's presence beacons stop — peers age us out of their radar and the
    // observed field failure ("iOS drops the mesh the instant it backgrounds")
    // follows, even though CoreBluetooth itself keeps running under the
    // declared bluetooth background modes. Fix: the JS layer hands us its most
    // recent safe liveness frame; while the app is backgrounded a native timer
    // re-broadcasts it every few seconds. The host remains responsible for making
    // that frame signed, replay-safe and incapable of triggering a product action.
    private var keepaliveData: String?
    private var keepaliveTimer: DispatchSourceTimer?
    private let keepaliveIntervalSeconds: Double = 4

    public override func load() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc private func appDidEnterBackground() {
        bleQueue.async { [self] in startKeepalive() }
    }

    @objc private func appWillEnterForeground() {
        bleQueue.async { [self] in stopKeepalive() }
    }

    @objc func setKeepaliveFrame(_ call: CAPPluginCall) {
        let data = call.getString("data", "")
        bleQueue.async { [self] in
            keepaliveData = data.isEmpty ? nil : data
            call.resolve()
        }
    }

    /// bleQueue only.
    private func startKeepalive() {
        guard running, keepaliveTimer == nil, keepaliveData != nil else { return }
        let timer = DispatchSource.makeTimerSource(queue: bleQueue)
        timer.schedule(deadline: .now() + keepaliveIntervalSeconds, repeating: keepaliveIntervalSeconds)
        timer.setEventHandler { [weak self] in self?.broadcastKeepalive() }
        timer.resume()
        keepaliveTimer = timer
    }

    /// bleQueue only.
    private func stopKeepalive() {
        keepaliveTimer?.cancel()
        keepaliveTimer = nil
    }

    /// bleQueue only.
    private func broadcastKeepalive() {
        guard running, let data = keepaliveData,
              let envelope = buildEnvelope(target: broadcastPeer, data: data),
              envelope.count <= maxEnvelopeBytes else { return }
        _ = writeEnvelope(target: broadcastPeer, envelope: envelope)
        txFrames += 1
    }

    @objc func start(_ call: CAPPluginCall) {
        let nextRoom = call.getString("room", "")
        let nextSelfId = call.getString("selfId", "")
        let serviceUuidText = call.getString("serviceUuid", "")
        guard !nextRoom.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty,
              !nextSelfId.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty,
              UUID(uuidString: serviceUuidText) != nil else {
            call.unavailable("room, selfId and serviceUuid are required")
            return
        }

        bleQueue.async { [self] in
            stopRadio()

            room = nextRoom
            selfId = nextSelfId
            serviceUUID = CBUUID(string: serviceUuidText)
            initialHops = bounded(call.getInt("hops", 0), minimum: 0, maximum: 8)
            maxEnvelopeBytes = bounded(call.getInt("maxEnvelopeBytes", 8192), minimum: 256, maximum: 65_536)
            maxSeenIds = bounded(call.getInt("maxSeenIds", 512), minimum: 32, maximum: 8192)
            reassemblyTtl = TimeInterval(
                bounded(call.getInt("reassemblyTtlMs", 30_000), minimum: 1000, maximum: 300_000)
            ) / 1000
            pendingStartCall = call
            txFrames = 0
            txChunks = 0
            rxFrames = 0
            rxChunks = 0
            droppedFrames = 0
            relayedFrames = 0
            lastError = nil

            centralManager = CBCentralManager(delegate: self, queue: bleQueue)
            peripheralManager = CBPeripheralManager(delegate: self, queue: bleQueue)
            emitStatus()
            tryStartRadio()
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        bleQueue.async { [self] in
            stopRadio()
            call.resolve()
        }
    }

    @objc func broadcast(_ call: CAPPluginCall) {
        bleQueue.async { [self] in sendFrame(call, target: broadcastPeer) }
    }

    @objc func send(_ call: CAPPluginCall) {
        let peer = call.getString("peer", "")
        guard !peer.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty else {
            call.unavailable("peer is required")
            return
        }
        bleQueue.async { [self] in sendFrame(call, target: peer) }
    }

    @objc func getStatus(_ call: CAPPluginCall) {
        bleQueue.async { [self] in call.resolve(statusPayload()) }
    }

    deinit {
        stopRadio()
    }

    private func statusPayload() -> [String: Any] {
        let peerPayloads = peripherals.values.map { peripheral -> [String: Any] in
            let id = peripheral.identifier
            let peerIds = peerPeripheralIds
                .filter { $0.value == id }
                .map { $0.key }
                .sorted()
            let queued = writeQueues[id]?.count ?? 0
            return [
                "uuid": id.uuidString,
                "peerIds": peerIds,
                "connected": true,
                "writable": writableCharacteristics[id] != nil,
                "mtu": peripheral.maximumWriteValueLength(for: .withResponse),
                "queuedChunks": queued,
                "writing": writingPeripherals.contains(id)
            ]
        }
        let writableCount = peripherals.keys.filter { writableCharacteristics[$0] != nil }.count
        let queuedCount = writeQueues.reduce(0) { partial, entry in
            partial + entry.value.count + (writingPeripherals.contains(entry.key) ? 1 : 0)
        }
        return [
            "native": true,
            "platform": "ios",
            "supported": managerState(centralManager?.state) != "unsupported" && managerState(peripheralManager?.state) != "unsupported",
            "running": running,
            "bluetooth": bluetoothState(),
            "centralState": managerState(centralManager?.state),
            "peripheralState": managerState(peripheralManager?.state),
            "permissions": permissionState(),
            "missingPermissions": [] as [String],
            "room": room ?? NSNull(),
            "selfId": selfId ?? NSNull(),
            "serviceUuid": serviceUUID?.uuidString ?? NSNull(),
            "advertising": advertisingActive,
            "scanning": scanningActive,
            "gattServer": frameCharacteristic != nil,
            "connectedPeers": peripherals.count,
            "writablePeers": writableCount,
            "knownPeers": peerPeripheralIds.count,
            "queuedChunks": queuedCount,
            "txFrames": txFrames,
            "txChunks": txChunks,
            "rxFrames": rxFrames,
            "rxChunks": rxChunks,
            "droppedFrames": droppedFrames,
            "relayedFrames": relayedFrames,
            "lastError": lastError ?? NSNull(),
            "peers": peerPayloads,
            "updatedAt": Int(Date().timeIntervalSince1970 * 1000)
        ]
    }

    private func managerState(_ state: CBManagerState?) -> String {
        guard let state else { return "unknown" }
        switch state {
        case .unknown:
            return "unknown"
        case .resetting:
            return "resetting"
        case .unsupported:
            return "unsupported"
        case .unauthorized:
            return "unauthorized"
        case .poweredOff:
            return "off"
        case .poweredOn:
            return "on"
        @unknown default:
            return "unknown"
        }
    }

    private func bluetoothState() -> String {
        let central = managerState(centralManager?.state)
        let peripheral = managerState(peripheralManager?.state)
        if central == "on" && peripheral == "on" { return "on" }
        if central == "unsupported" || peripheral == "unsupported" { return "unsupported" }
        if central == "unauthorized" || peripheral == "unauthorized" { return "unauthorized" }
        if central == "off" || peripheral == "off" { return "off" }
        return "\(central)/\(peripheral)"
    }

    private func permissionState() -> String {
        switch CBManager.authorization {
        case .allowedAlways:
            return "granted"
        case .denied:
            return "denied"
        case .restricted:
            return "restricted"
        case .notDetermined:
            return "not-determined"
        @unknown default:
            return "unknown"
        }
    }

    private func emitStatus() {
        notifyListeners(eventStatus, data: statusPayload())
    }

    private func rememberError(_ message: String) {
        lastError = message
        emitStatus()
    }

    private func dropInboundFrame() {
        droppedFrames += 1
        emitStatus()
    }

    private func tryStartRadio() {
        guard let call = pendingStartCall else { return }
        guard let central = centralManager, let peripheral = peripheralManager else { return }

        if let unavailable = unavailableReason(central.state) ?? unavailableReason(peripheral.state) {
            pendingStartCall = nil
            lastError = unavailable
            stopRadio()
            call.unavailable(unavailable)
            return
        }

        guard central.state == .poweredOn, peripheral.state == .poweredOn else { return }

        running = true
        startPeripheralRole()
        startCentralRole()
        pendingStartCall = nil
        call.resolve()
        emitStatus()
    }

    private func unavailableReason(_ state: CBManagerState) -> String? {
        switch state {
        case .unsupported:
            return "Bluetooth LE is not supported on this device"
        case .unauthorized:
            return "Bluetooth permission is denied"
        case .poweredOff:
            return "Bluetooth is off"
        default:
            return nil
        }
    }

    private func startPeripheralRole() {
        guard let manager = peripheralManager, let serviceUUID else { return }

        manager.removeAllServices()
        let characteristic = CBMutableCharacteristic(
            type: frameCharacteristicUUID,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        let service = CBMutableService(type: serviceUUID, primary: true)
        service.characteristics = [characteristic]
        frameCharacteristic = characteristic
        manager.add(service)
        emitStatus()
    }

    private func startAdvertising() {
        guard let manager = peripheralManager, let serviceUUID, running, manager.state == .poweredOn else { return }
        if manager.isAdvertising {
            manager.stopAdvertising()
        }
        advertisingActive = false
        manager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID]
        ])
        emitStatus()
    }

    private func startCentralRole() {
        guard let central = centralManager, let serviceUUID, running, central.state == .poweredOn else { return }
        central.stopScan()
        central.scanForPeripherals(
            withServices: [serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
        scanningActive = true
        emitStatus()
    }

    private func stopRadio() {
        stopKeepalive()
        keepaliveData = nil
        running = false
        advertisingActive = false
        scanningActive = false
        pendingStartCall = nil

        centralManager?.stopScan()
        for peripheral in peripherals.values {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()

        centralManager = nil
        peripheralManager = nil
        frameCharacteristic = nil
        serviceUUID = nil
        room = nil
        selfId = nil

        peripherals.removeAll()
        writableCharacteristics.removeAll()
        writeQueues.removeAll()
        writingPeripherals.removeAll()
        inbound.removeAll()
        peerPeripheralIds.removeAll()
        seenIds.removeAll()
        seenIdSet.removeAll()
        emitStatus()
    }

    private func sendFrame(_ call: CAPPluginCall, target: String) {
        guard running else {
            rememberError("native BLE transport is not running")
            call.unavailable("native BLE transport is not running")
            return
        }
        let data = call.getString("data", "")
        guard !data.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty else {
            rememberError("BLE frame data is required")
            call.unavailable("data is required")
            return
        }
        guard let envelope = buildEnvelope(target: target, data: data) else {
            rememberError("could not encode BLE envelope")
            call.unavailable("could not encode BLE envelope")
            return
        }
        guard envelope.count <= maxEnvelopeBytes else {
            rememberError("BLE frame is too large")
            call.unavailable("BLE frame is too large")
            return
        }

        let queued = writeEnvelope(target: target, envelope: envelope)
        txFrames += 1
        call.resolve(["queuedPeers": queued])
        emitStatus()
    }

    private func buildEnvelope(target: String, data: String) -> Data? {
        guard let room, let selfId else { return nil }
        // "h" = multi-hop TTL (hops remaining); see the Android plugin doc. A
        // receiver re-floods with h-1 while h > 0, so a frame reaches up to
        // initialHops links away; the message-id dedup keeps it loop-safe.
        let envelope: [String: Any] = [
            "v": 1,
            "r": room,
            "t": target,
            "f": selfId,
            "id": UUID().uuidString,
            "h": initialHops,
            "d": data
        ]
        return try? JSONSerialization.data(withJSONObject: envelope, options: [])
    }

    private func writeEnvelope(target: String, envelope: Data) -> Int {
        let targets = targetPeripherals(target)
        for peripheral in targets {
            let chunks = chunksFor(peripheral: peripheral, envelope: envelope)
            txChunks += chunks.count
            enqueue(chunks, for: peripheral)
        }
        return targets.count
    }

    /// Re-flood a received envelope to every writable peripheral EXCEPT the source.
    private func floodEnvelope(_ envelope: Data, exceptSource source: String) {
        let exceptId = UUID(uuidString: source)
        for peripheral in Array(peripherals.values) where writableCharacteristics[peripheral.identifier] != nil {
            if peripheral.identifier == exceptId { continue }
            let chunks = chunksFor(peripheral: peripheral, envelope: envelope)
            txChunks += chunks.count
            enqueue(chunks, for: peripheral)
        }
    }

    private func targetPeripherals(_ target: String) -> [CBPeripheral] {
        if target != broadcastPeer,
           let peripheralId = peerPeripheralIds[target],
           let peripheral = peripherals[peripheralId],
           writableCharacteristics[peripheralId] != nil {
            return [peripheral]
        }
        return Array(peripherals.values).filter { writableCharacteristics[$0.identifier] != nil }
    }

    private func chunksFor(peripheral: CBPeripheral, envelope: Data) -> [Data] {
        let maximumWrite = peripheral.maximumWriteValueLength(for: .withResponse)
        let payloadBytes = max(1, min(maxChunkPayload, maximumWrite - chunkHeaderBytes))
        let total = max(1, Int(ceil(Double(envelope.count) / Double(payloadBytes))))
        guard total <= maxChunks else { return [] }

        let messageId = UInt32.random(in: UInt32.min...UInt32.max)
        var chunks = [Data]()
        chunks.reserveCapacity(total)

        for index in 0..<total {
            let from = index * payloadBytes
            let to = min(envelope.count, from + payloadBytes)
            var chunk = Data()
            chunk.append(chunkMagic)
            chunk.append(chunkVersion)
            chunk.append(UInt8((messageId >> 24) & 0xff))
            chunk.append(UInt8((messageId >> 16) & 0xff))
            chunk.append(UInt8((messageId >> 8) & 0xff))
            chunk.append(UInt8(messageId & 0xff))
            chunk.append(UInt8(index))
            chunk.append(UInt8(total))
            if from < to {
                chunk.append(envelope.subdata(in: from..<to))
            }
            chunks.append(chunk)
        }

        return chunks
    }

    private func enqueue(_ chunks: [Data], for peripheral: CBPeripheral) {
        guard !chunks.isEmpty else { return }
        let id = peripheral.identifier
        writeQueues[id, default: []].append(contentsOf: chunks)
        flush(peripheral)
        emitStatus()
    }

    private func flush(_ peripheral: CBPeripheral) {
        let id = peripheral.identifier
        guard running, !writingPeripherals.contains(id),
              let characteristic = writableCharacteristics[id],
              var queue = writeQueues[id],
              !queue.isEmpty else {
            return
        }

        let next = queue.removeFirst()
        writeQueues[id] = queue
        writingPeripherals.insert(id)
        peripheral.writeValue(next, for: characteristic, type: .withResponse)
        emitStatus()
    }

    private func handleChunk(source: String, data: Data) {
        guard data.count >= chunkHeaderBytes else {
            dropInboundFrame()
            return
        }
        let bytes = [UInt8](data)
        guard bytes[0] == chunkMagic, bytes[1] == chunkVersion else {
            dropInboundFrame()
            return
        }
        rxChunks += 1

        let messageId = UInt32(bytes[2]) << 24 | UInt32(bytes[3]) << 16 | UInt32(bytes[4]) << 8 | UInt32(bytes[5])
        let index = Int(bytes[6])
        let total = Int(bytes[7])
        guard total >= 1, index < total else {
            dropInboundFrame()
            return
        }

        pruneReassembly()

        let key = "\(source):\(messageId)"
        var reassembly = inbound[key] ?? Reassembly(total: total)
        guard reassembly.total == total else {
            inbound.removeValue(forKey: key)
            dropInboundFrame()
            return
        }

        let payload = data.subdata(in: chunkHeaderBytes..<data.count)
        if reassembly.add(index: index, data: payload, maxBytes: maxEnvelopeBytes) {
            inbound.removeValue(forKey: key)
            if let envelope = reassembly.join(), envelope.count <= maxEnvelopeBytes {
                handleEnvelope(source: source, data: envelope)
            } else {
                dropInboundFrame()
            }
        } else if reassembly.bytes > maxEnvelopeBytes {
            inbound.removeValue(forKey: key)
            dropInboundFrame()
        } else {
            inbound[key] = reassembly
        }
        emitStatus()
    }

    private func handleEnvelope(source: String, data: Data) {
        guard let object = try? JSONSerialization.jsonObject(with: data),
              var envelope = object as? [String: Any],
              number(envelope["v"]) == 1,
              let envelopeRoom = envelope["r"] as? String,
              let from = envelope["f"] as? String,
              let target = envelope["t"] as? String,
              let id = envelope["id"] as? String,
              let frameData = envelope["d"] as? String,
              let room,
              let selfId else {
            dropInboundFrame()
            return
        }

        guard envelopeRoom == room else {
            dropInboundFrame()
            return
        }
        guard !from.isEmpty, from != selfId else {
            dropInboundFrame()
            return
        }
        guard !target.isEmpty, !id.isEmpty, !frameData.isEmpty else {
            dropInboundFrame()
            return
        }
        // Dedup ONCE per id — gates delivery AND re-flood, so a frame circulating
        // the mesh is handled at most once per node.
        guard rememberSeen(id) else {
            dropInboundFrame()
            return
        }

        if let peripheralId = UUID(uuidString: source), writableCharacteristics[peripheralId] != nil {
            peerPeripheralIds[from] = peripheralId
        }

        let forUs = target == broadcastPeer || target == selfId
        // Broadcasts flood onward; a directed frame for someone else floods on
        // toward its target; a directed frame for us stops here.
        let relay = target == broadcastPeer || target != selfId
        let hops = number(envelope["h"]) ?? 0
        if relay, hops > 0 {
            envelope["h"] = hops - 1
            if let onward = try? JSONSerialization.data(withJSONObject: envelope, options: []),
               onward.count <= maxEnvelopeBytes {
                floodEnvelope(onward, exceptSource: source)
                relayedFrames += 1
            }
        }

        guard forUs else {
            emitStatus()
            return
        }

        rxFrames += 1
        notifyListeners(eventFrame, data: [
            "from": from,
            "data": frameData
        ])
        emitStatus()
    }

    private func number(_ value: Any?) -> Int? {
        if let value = value as? Int { return value }
        if let value = value as? NSNumber { return value.intValue }
        return nil
    }

    private func bounded(_ value: Int, minimum: Int, maximum: Int) -> Int {
        return max(minimum, min(maximum, value))
    }

    private func rememberSeen(_ id: String) -> Bool {
        if seenIdSet.contains(id) { return false }
        seenIdSet.insert(id)
        seenIds.append(id)
        while seenIds.count > maxSeenIds {
            let removed = seenIds.removeFirst()
            seenIdSet.remove(removed)
        }
        return true
    }

    private func pruneReassembly() {
        let now = Date()
        inbound = inbound.filter { now.timeIntervalSince($0.value.createdAt) <= reassemblyTtl }
    }

    private func dropPeripheral(_ peripheral: CBPeripheral) {
        let id = peripheral.identifier
        peripherals.removeValue(forKey: id)
        writableCharacteristics.removeValue(forKey: id)
        writeQueues.removeValue(forKey: id)
        writingPeripherals.remove(id)
        peerPeripheralIds = peerPeripheralIds.filter { $0.value != id }
        emitStatus()
    }

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        tryStartRadio()
        emitStatus()
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard running, peripherals[peripheral.identifier] == nil else { return }
        peripherals[peripheral.identifier] = peripheral
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
        emitStatus()
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard running, let serviceUUID else { return }
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
        emitStatus()
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        if let error {
            rememberError("BLE connection failed: \(error.localizedDescription)")
        }
        dropPeripheral(peripheral)
        startCentralRole()
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        if let error {
            lastError = "BLE peer disconnected: \(error.localizedDescription)"
        }
        dropPeripheral(peripheral)
        startCentralRole()
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard running, error == nil else {
            if let error {
                rememberError("BLE service discovery failed: \(error.localizedDescription)")
            }
            dropPeripheral(peripheral)
            return
        }
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == serviceUUID {
            peripheral.discoverCharacteristics([frameCharacteristicUUID], for: service)
            emitStatus()
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard running, error == nil, let characteristics = service.characteristics else {
            if let error {
                rememberError("BLE characteristic discovery failed: \(error.localizedDescription)")
            }
            dropPeripheral(peripheral)
            return
        }
        for characteristic in characteristics where characteristic.uuid == frameCharacteristicUUID {
            writableCharacteristics[peripheral.identifier] = characteristic
            flush(peripheral)
            emitStatus()
            return
        }
        rememberError("BLE peer did not expose the mesh frame characteristic")
        dropPeripheral(peripheral)
    }

    public func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        writingPeripherals.remove(peripheral.identifier)
        guard error == nil else {
            if let error {
                rememberError("BLE characteristic write failed: \(error.localizedDescription)")
            }
            dropPeripheral(peripheral)
            return
        }
        flush(peripheral)
        emitStatus()
    }

    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        tryStartRadio()
        emitStatus()
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        guard running, error == nil else {
            if let error {
                rememberError("BLE service registration failed: \(error.localizedDescription)")
            }
            return
        }
        startAdvertising()
    }

    public func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            advertisingActive = false
            rememberError("BLE advertising failed: \(error.localizedDescription)")
        } else {
            advertisingActive = true
            emitStatus()
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        var result = CBATTError.success
        for request in requests {
            guard request.characteristic.uuid == frameCharacteristicUUID, let value = request.value else {
                result = .requestNotSupported
                dropInboundFrame()
                continue
            }
            handleChunk(source: request.central.identifier.uuidString, data: value)
        }
        if let first = requests.first {
            peripheral.respond(to: first, withResult: result)
        }
    }

    private struct Reassembly {
        let total: Int
        let createdAt = Date()
        var chunks: [Int: Data] = [:]
        var bytes = 0

        mutating func add(index: Int, data: Data, maxBytes: Int) -> Bool {
            guard chunks[index] == nil else { return false }
            chunks[index] = data
            bytes += data.count
            return chunks.count == total && bytes <= maxBytes
        }

        func join() -> Data? {
            var joined = Data()
            for index in 0..<total {
                guard let chunk = chunks[index] else { return nil }
                joined.append(chunk)
            }
            return joined
        }
    }
}
