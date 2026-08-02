//! Build orchestration: UniFFI bindings, Android NDK libraries, iOS XCFramework.

use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus};

use anyhow::{bail, Context, Result};
use camino::Utf8PathBuf;
use clap::{Parser, Subcommand};

#[derive(Parser)]
#[command(name = "xtask", about = "NofAR Rust build tasks")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Generate Kotlin bindings into :core:ffi.
    KotlinBindings {
        #[arg(long, default_value = "../core/ffi/src/generated/kotlin")]
        out_dir: Utf8PathBuf,
    },
    /// Generate Swift bindings and modulemap for NofARCoreBridge.
    SwiftBindings {
        #[arg(
            long,
            default_value = "../ios/NofARCoreBridge/Sources/NofARCoreBridge/Generated"
        )]
        out_dir: Utf8PathBuf,
    },
    /// Build host `cdylib` for JVM unit tests (never packaged in APK).
    HostLib {
        #[arg(long, default_value = "../core/ffi/build/rust/host")]
        out_dir: Utf8PathBuf,
    },
    /// Build Android `.so` artifacts for arm64-v8a and x86_64 via cargo-ndk.
    Android {
        #[arg(long, default_value = "../core/ffi/build/rust/android")]
        out_dir: Utf8PathBuf,
    },
    /// Bootstrap iOS: Swift bindings + XCFramework (requires Xcode toolchains).
    Ios {
        #[arg(
            long,
            default_value = "../ios/NofARCoreBridge/Artifacts/NofARCore.xcframework"
        )]
        xcframework: Utf8PathBuf,
    },
}

fn main() -> Result<()> {
    let workspace_root = workspace_root()?;
    std::env::set_var(
        "NOFAR_CARGO_TARGET_DIR",
        workspace_root.join("target").as_str(),
    );
    let cli = Cli::parse();
    match cli.command {
        Commands::KotlinBindings { out_dir } => {
            std::fs::create_dir_all(&out_dir)?;
            run_bindgen(&workspace_root, "kotlin", &out_dir)?;
        }
        Commands::SwiftBindings { out_dir } => {
            std::fs::create_dir_all(&out_dir)?;
            run_bindgen(&workspace_root, "swift", &out_dir)?;
        }
        Commands::HostLib { out_dir } => {
            std::fs::create_dir_all(&out_dir)?;
            let mut cmd = Command::new("cargo");
            cmd.current_dir(&workspace_root)
                .args(["build", "--release", "--package", "nofar-ffi"]);
            run(cmd, "cargo build host lib")?;
            copy_host_artifact(&workspace_root, "", &out_dir)?;
        }
        Commands::Android { out_dir } => {
            std::fs::create_dir_all(&out_dir)?;
            android_build(&workspace_root, &out_dir)?;
        }
        Commands::Ios { xcframework } => {
            if let Some(parent) = xcframework.parent() {
                std::fs::create_dir_all(parent)?;
            }
            ios_xcframework(&workspace_root, &xcframework)?;
        }
    }
    Ok(())
}

fn workspace_root() -> Result<Utf8PathBuf> {
    let manifest_dir = Utf8PathBuf::from_path_buf(std::env::current_dir()?)
        .map_err(|_| anyhow::anyhow!("workspace path is not valid UTF-8"))?;
    if manifest_dir.join("Cargo.toml").exists() && manifest_dir.join("nofar-ffi").exists() {
        return Ok(manifest_dir);
    }
    if manifest_dir.ends_with("xtask") {
        return Ok(manifest_dir.parent().context("xtask parent")?.to_path_buf());
    }
    bail!("run xtask from the rust/ workspace directory");
}

fn run_bindgen(workspace: &Utf8PathBuf, language: &str, out_dir: &Utf8PathBuf) -> Result<()> {
    let mut cmd = Command::new("cargo");
    cmd.current_dir(workspace)
        .args(["build", "--release", "--package", "nofar-ffi"]);
    run(cmd, "cargo build host cdylib for bindgen")?;
    let lib_path = workspace.join(format!("target/release/{}", host_lib_filename()));
    let mut cmd = Command::new("cargo");
    cmd.current_dir(workspace).args([
        "run",
        "--quiet",
        "--package",
        "nofar-ffi",
        "--bin",
        "uniffi-bindgen",
        "--",
        "generate",
        lib_path.as_str(),
        "--library",
        "--no-format",
        "--language",
        language,
        "--out-dir",
        out_dir.as_str(),
    ]);
    run(cmd, "uniffi-bindgen generate")?;
    Ok(())
}

fn cargo_build(workspace: &Utf8PathBuf, package: &str, target: &str, profile: &str) -> Result<()> {
    let mut cmd = Command::new("cargo");
    cmd.current_dir(workspace).args([
        "build",
        "--package",
        package,
        "--target",
        target,
        "--profile",
        profile,
    ]);
    run(cmd, "cargo build")?;
    Ok(())
}

fn copy_host_artifact(workspace: &Utf8PathBuf, target: &str, out_dir: &Utf8PathBuf) -> Result<()> {
    let profile = "release";
    let lib_name = host_lib_filename();
    let src = if target.is_empty() {
        workspace.join(format!("target/{profile}/{lib_name}"))
    } else {
        workspace.join(format!("target/{target}/{profile}/{lib_name}"))
    };
    let dst = out_dir.join(lib_name);
    std::fs::copy(&src, &dst).with_context(|| format!("copy {} -> {}", src, dst))?;
    Ok(())
}

fn host_lib_filename() -> &'static str {
    if cfg!(target_os = "macos") {
        "libnofar_ffi.dylib"
    } else if cfg!(target_os = "linux") {
        "libnofar_ffi.so"
    } else if cfg!(windows) {
        "nofar_ffi.dll"
    } else {
        "libnofar_ffi.so"
    }
}

fn android_build(workspace: &Utf8PathBuf, out_dir: &Utf8PathBuf) -> Result<()> {
    let ndk_home = std::env::var("ANDROID_NDK_HOME")
        .or_else(|_| std::env::var("NDK_HOME"))
        .context("ANDROID_NDK_HOME or NDK_HOME must be set for Android Rust builds")?;
    let _ = ndk_home;

    for (abi, triple) in [
        ("arm64-v8a", "aarch64-linux-android"),
        ("x86_64", "x86_64-linux-android"),
    ] {
        let mut cmd = Command::new("cargo");
        cmd.current_dir(workspace).args([
            "ndk",
            "--target",
            triple,
            "--platform",
            "26",
            "--",
            "build",
            "--release",
            "--package",
            "nofar-ffi",
        ]);
        run(cmd, "cargo ndk build")?;
        let src = workspace.join(format!("target/{triple}/release/libnofar_ffi.so"));
        let dst_dir = out_dir.join(abi);
        std::fs::create_dir_all(&dst_dir)?;
        std::fs::copy(&src, dst_dir.join("libnofar_ffi.so"))
            .with_context(|| format!("copy Android lib for {abi}"))?;
    }
    Ok(())
}

fn ios_xcframework(workspace: &Utf8PathBuf, xcframework: &Utf8PathBuf) -> Result<()> {
    let swift_out = workspace.join("../ios/NofARCoreBridge/Sources/NofARCoreBridge/Generated");
    std::fs::create_dir_all(&swift_out)?;
    run_bindgen(workspace, "swift", &swift_out)?;

    let out_dir = workspace.join("target/ios");
    std::fs::create_dir_all(&out_dir)?;

    for (target, lib_name) in [
        ("aarch64-apple-ios", "libnofar_ffi.a"),
        ("aarch64-apple-ios-sim", "libnofar_ffi_sim_arm64.a"),
        ("x86_64-apple-ios", "libnofar_ffi_sim_x86.a"),
    ] {
        cargo_build(workspace, "nofar-ffi", target, "release")?;
        let src = workspace.join(format!("target/{target}/release/libnofar_ffi.a"));
        std::fs::copy(&src, out_dir.join(lib_name))?;
    }

    let sim_fat = out_dir.join("libnofar_ffi_sim.a");
    run_lipo(
        &[
            out_dir.join("libnofar_ffi_sim_arm64.a").into_std_path_buf(),
            out_dir.join("libnofar_ffi_sim_x86.a").into_std_path_buf(),
        ],
        sim_fat.as_std_path(),
    )?;

    if xcframework.exists() {
        std::fs::remove_dir_all(xcframework)?;
    }

    let mut cmd = Command::new("xcodebuild");
    cmd.args([
        "-create-xcframework",
        "-library",
        out_dir.join("libnofar_ffi.a").as_str(),
        "-headers",
        swift_out.as_str(),
        "-library",
        sim_fat.as_str(),
        "-headers",
        swift_out.as_str(),
        "-output",
        xcframework.as_str(),
    ]);
    run(cmd, "xcodebuild -create-xcframework")?;
    Ok(())
}

fn run_lipo(inputs: &[PathBuf], output: &Path) -> Result<()> {
    let mut cmd = Command::new("lipo");
    cmd.arg("-create");
    for input in inputs {
        cmd.arg(input);
    }
    cmd.arg("-output").arg(output);
    run(cmd, "lipo")?;
    Ok(())
}

fn run(mut cmd: Command, label: &str) -> Result<ExitStatus> {
    if let Ok(target_dir) = std::env::var("NOFAR_CARGO_TARGET_DIR") {
        cmd.env("CARGO_TARGET_DIR", target_dir);
    }
    let status = cmd
        .status()
        .with_context(|| format!("failed to spawn {label}"))?;
    if !status.success() {
        bail!("{label} failed with {status}");
    }
    Ok(status)
}
