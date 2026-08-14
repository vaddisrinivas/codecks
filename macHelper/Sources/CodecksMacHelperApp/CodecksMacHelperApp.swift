import CodecksMacHelper
import SwiftUI

@main
struct CodecksMacHelperApp: App {
    @StateObject private var model = HelperAppModel()

    var body: some Scene {
        MenuBarExtra("Codecks Mac Helper", systemImage: model.menuBarSymbol) {
            HelperMenuView(model: model)
        }
        .menuBarExtraStyle(.window)

        Window("Codecks Mac Helper", id: "setup") {
            HelperSetupView(model: model)
                .frame(minWidth: 580, idealWidth: 640, minHeight: 540, idealHeight: 620)
        }
        .defaultSize(width: 640, height: 620)
    }
}
