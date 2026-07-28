import Foundation

public enum ReactiveFrameCodec {
    public static func encode(_ payload: Data) throws -> Data {
        guard !payload.isEmpty else { throw ReactiveValidationError("frame payload must not be empty") }
        guard payload.count <= ReactiveConstants.maxBodyBytes else { throw ReactiveValidationError("frame payload exceeds limit") }
        let size = UInt32(payload.count).bigEndian
        var frame = Data()
        withUnsafeBytes(of: size) { frame.append(contentsOf: $0) }
        frame.append(payload)
        return frame
    }

    public static func decode(_ frame: Data) throws -> Data {
        guard frame.count >= 4 else { throw ReactiveValidationError("frame header incomplete") }
        let size = frame.prefix(4).reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        guard size >= 1 && size <= UInt32(ReactiveConstants.maxBodyBytes) else {
            throw ReactiveValidationError("invalid frame length")
        }
        guard frame.count == Int(size) + 4 else { throw ReactiveValidationError("frame length mismatch") }
        return frame.dropFirst(4)
    }
}
