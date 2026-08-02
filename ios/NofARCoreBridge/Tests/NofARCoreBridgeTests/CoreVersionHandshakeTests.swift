import XCTest
@testable import NofARCoreBridge

final class CoreVersionHandshakeTests: XCTestCase {
    func testVerifyAcceptsCurrentBindings() throws {
        try CoreVersionHandshake.verify()
    }
}
