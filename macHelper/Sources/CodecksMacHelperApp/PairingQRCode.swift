import CoreImage.CIFilterBuiltins
import SwiftUI

enum PairingQRCode {
    static func image(payload: String) -> NSImage? {
        guard !payload.isEmpty, payload.utf8.count <= 4 * 1024 else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)) else { return nil }
        let representation = NSCIImageRep(ciImage: output)
        let image = NSImage(size: representation.size)
        image.addRepresentation(representation)
        image.accessibilityDescription = "Codecks pairing QR code"
        return image
    }
}
