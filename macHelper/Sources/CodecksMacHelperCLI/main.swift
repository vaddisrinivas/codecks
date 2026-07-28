import CodecksMacHelper
import Foundation

let args = Array(CommandLine.arguments.dropFirst())

do {
    switch args.first {
    case "serve":
        let config = try ReactiveMacHelperRuntimeConfig.environment()
        let coordinator = config.makeCoordinator()
        let framed = ReactiveFramedTransportService(coordinator: coordinator, secret: config.sharedSecret)
        let server = ReactiveTcpHelperServer(port: config.port, handler: framed)
        try server.start()
        print("codecks-mac-helper serving on port \(config.port)")
        RunLoop.main.run()
    case "check-config":
        _ = try ReactiveMacHelperRuntimeConfig.environment()
        print("codecks-mac-helper config ok")
    case "print-pairing-json":
        let config = try ReactiveMacHelperRuntimeConfig.environment()
        let host = args.dropFirst().first
        print(try config.pairingPayloadJson(host: host))
    default:
        print("usage: codecks-mac-helper serve | check-config | print-pairing-json [host]")
        exit(2)
    }
} catch {
    FileHandle.standardError.write(Data("codecks-mac-helper: \(error)\n".utf8))
    exit(1)
}
