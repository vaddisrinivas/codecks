// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CodecksMacHelper",
    platforms: [.macOS(.v13)],
    products: [
        .library(name: "CodecksMacHelper", targets: ["CodecksMacHelper"]),
        .executable(name: "codecks-mac-helper", targets: ["CodecksMacHelperCLI"]),
        .executable(name: "Codecks Mac Helper", targets: ["CodecksMacHelperApp"]),
    ],
    targets: [
        .target(name: "CodecksMacHelper"),
        .executableTarget(
            name: "CodecksMacHelperCLI",
            dependencies: ["CodecksMacHelper"]
        ),
        .executableTarget(
            name: "CodecksMacHelperApp",
            dependencies: ["CodecksMacHelper"]
        ),
        .testTarget(
            name: "CodecksMacHelperTests",
            dependencies: ["CodecksMacHelper"]
        ),
    ]
)
