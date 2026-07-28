import Foundation

public enum ReactiveTransportRoute: Equatable {
    case hello
    case proof
    case request
}

public final class ReactiveFramedTransportService {
    private let coordinator: ReactiveSessionCoordinator
    private let secret: Data
    private let jsonDecoder = JSONDecoder()
    private let jsonEncoder = JSONEncoder()

    public init(coordinator: ReactiveSessionCoordinator, secret: Data) {
        self.coordinator = coordinator
        self.secret = secret
        jsonEncoder.outputFormatting = [.sortedKeys]
    }

    public func handleFrame(_ frame: Data, nowMillis: Int64 = nowMillis()) throws -> Data {
        let payload = try ReactiveFrameCodec.decode(frame)
        let responsePayload = try handlePayload(payload, nowMillis: nowMillis)
        return try ReactiveFrameCodec.encode(responsePayload)
    }

    public func handlePayload(_ payload: Data, nowMillis: Int64 = nowMillis()) throws -> Data {
        switch try route(payload) {
        case .hello:
            let hello = try jsonDecoder.decode(ReactiveHello.self, from: payload)
            return try jsonEncoder.encode(coordinator.handleHello(hello, nowMillis: nowMillis))
        case .proof:
            let proof = try jsonDecoder.decode(ReactiveProof.self, from: payload)
            return try jsonEncoder.encode(coordinator.handleProof(proof, secret: secret, nowMillis: nowMillis))
        case .request:
            let envelope = try jsonDecoder.decode(ReactiveRequestEnvelope.self, from: payload)
            return try jsonEncoder.encode(coordinator.handleRequest(envelope, nowMillis: nowMillis))
        }
    }

    public func route(_ payload: Data) throws -> ReactiveTransportRoute {
        guard
            let object = try JSONSerialization.jsonObject(with: payload) as? [String: Any]
        else {
            throw ReactiveValidationError("transport payload must be a JSON object")
        }
        if object["deviceId"] != nil, object["clientNonce"] != nil {
            return .hello
        }
        if object["sessionId"] != nil, object["proof"] != nil, object["pinAcknowledgement"] != nil {
            return .proof
        }
        if object["sessionId"] != nil, object["requestId"] != nil, object["bodyJson"] != nil, object["authTag"] != nil {
            return .request
        }
        throw ReactiveValidationError("unknown transport payload")
    }
}
