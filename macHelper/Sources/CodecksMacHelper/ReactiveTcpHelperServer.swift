import Foundation
import Network

public protocol ReactiveFrameHandling {
    func handleFrame(_ frame: Data, nowMillis: Int64) throws -> Data
}

extension ReactiveFramedTransportService: ReactiveFrameHandling {}

public final class ReactiveTcpHelperServer: @unchecked Sendable {
    private let port: UInt16
    private let handler: ReactiveFrameHandling
    private let queue: DispatchQueue
    private let serviceName: String?
    private var listener: NWListener?
    private var connections: Set<NWConnectionBox> = []

    public init(
        port: UInt16,
        handler: ReactiveFrameHandling,
        serviceName: String? = "Codecks Mac Helper",
        queue: DispatchQueue = DispatchQueue(label: "app.codecks.mac-helper.tcp")
    ) {
        self.port = port
        self.handler = handler
        self.serviceName = serviceName
        self.queue = queue
    }

    public func start() throws {
        let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: port)!)
        if let serviceName, !serviceName.isEmpty {
            listener.service = NWListener.Service(
                name: serviceName,
                type: "_codecks-reactive._tcp"
            )
        }
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        listener.start(queue: queue)
        self.listener = listener
    }

    public func stop() {
        listener?.cancel()
        listener = nil
        connections.forEach { $0.connection.cancel() }
        connections.removeAll()
    }

    private func accept(_ connection: NWConnection) {
        let box = NWConnectionBox(connection)
        connections.insert(box)
        connection.stateUpdateHandler = { [weak self, weak box] state in
            if case .failed = state, let box {
                self?.connections.remove(box)
            }
            if case .cancelled = state, let box {
                self?.connections.remove(box)
            }
        }
        connection.start(queue: queue)
        receiveHeader(on: connection)
    }

    private func receiveHeader(on connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 4, maximumLength: 4) { [weak self, weak connection] data, _, isComplete, error in
            guard let self, let connection else { return }
            guard error == nil, !isComplete, let data, data.count == 4 else {
                connection.cancel()
                return
            }
            let length = data.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
            guard length >= 1, length <= UInt32(ReactiveConstants.maxBodyBytes) else {
                connection.cancel()
                return
            }
            receivePayload(header: data, length: Int(length), on: connection)
        }
    }

    private func receivePayload(header: Data, length: Int, on connection: NWConnection) {
        connection.receive(minimumIncompleteLength: length, maximumLength: length) { [weak self, weak connection] data, _, isComplete, error in
            guard let self, let connection else { return }
            guard error == nil, !isComplete, let data, data.count == length else {
                connection.cancel()
                return
            }
            var frame = Data()
            frame.append(header)
            frame.append(data)
            do {
                let response = try handler.handleFrame(frame, nowMillis: nowMillis())
                connection.send(content: response, completion: .contentProcessed { [weak self, weak connection] error in
                    guard let self, let connection, error == nil else {
                        connection?.cancel()
                        return
                    }
                    self.receiveHeader(on: connection)
                })
            } catch {
                connection.cancel()
            }
        }
    }
}

private final class NWConnectionBox: Hashable, @unchecked Sendable {
    let connection: NWConnection

    init(_ connection: NWConnection) {
        self.connection = connection
    }

    static func == (lhs: NWConnectionBox, rhs: NWConnectionBox) -> Bool {
        lhs === rhs
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self))
    }
}
