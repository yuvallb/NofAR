import Foundation

/// App-facing adapter; generated UniFFI types stay package-internal until mapped here (MP0).
public enum CoreVersionHandshake {
    public static let expectedUniFFIBindingsVersion: UInt32 = 1

    public static func verify() throws {
        let bindings = try uniffiBindingsVersion()
        guard bindings == expectedUniFFIBindingsVersion else {
            throw CoreClientError.bindingsVersionMismatch(expected: expectedUniFFIBindingsVersion, actual: bindings)
        }
        let api = try coreApiVersion()
        guard api == expectedUniFFIBindingsVersion else {
            throw CoreClientError.apiVersionMismatch(expected: expectedUniFFIBindingsVersion, actual: api)
        }
    }
}

public enum CoreClientError: Error, Sendable {
    case bindingsVersionMismatch(expected: UInt32, actual: UInt32)
    case apiVersionMismatch(expected: UInt32, actual: UInt32)
}

#if canImport(NofARCoreFFI)
import NofARCoreFFI

private func coreApiVersion() throws -> UInt32 {
    nofar.coreApiVersion()
}

private func uniffiBindingsVersion() throws -> UInt32 {
    nofar.uniffiBindingsVersion()
}
#else
private func coreApiVersion() throws -> UInt32 { 1 }
private func uniffiBindingsVersion() throws -> UInt32 { 1 }
#endif
