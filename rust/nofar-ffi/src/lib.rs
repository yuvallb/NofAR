use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use nofar_core::{CORE_API_VERSION, UNIFFI_BINDINGS_VERSION};

uniffi::setup_scaffolding!();

const MAX_PATH_LEN: usize = 4096;

#[derive(uniffi::Record)]
pub struct CorePaths {
    pub database_file: String,
    pub dem_root: String,
    pub staging_root: String,
    pub temp_root: String,
}

#[derive(uniffi::Enum)]
pub enum CoreMode {
    ComputeOnly,
    Persistence,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum NofarError {
    #[error("corrupt DEM tile")]
    CorruptDemTile,
    #[error("unsupported GeoTIFF")]
    UnsupportedGeoTiff,
    #[error("overpass parse error")]
    OverpassParse,
    #[error("database error")]
    Database,
    #[error("migration error")]
    Migration,
    #[error("invalid region")]
    InvalidRegion,
    #[error("resource limit")]
    ResourceLimit,
    #[error("cancelled")]
    Cancelled,
    #[error("I/O error")]
    Io,
    #[error("internal error")]
    Internal,
}

#[derive(uniffi::Record)]
pub struct DatabaseChanges {
    pub regions: bool,
    pub entities: bool,
    pub storage: bool,
    pub prepare_progress: bool,
}

fn validate_path(path: &str) -> Result<(), NofarError> {
    if path.is_empty() {
        return Err(NofarError::InvalidRegion);
    }
    if path.len() > MAX_PATH_LEN {
        return Err(NofarError::ResourceLimit);
    }
    if path.contains('\0') {
        return Err(NofarError::InvalidRegion);
    }
    Ok(())
}

fn validate_core_paths(paths: &CorePaths) -> Result<(), NofarError> {
    validate_path(&paths.database_file)?;
    validate_path(&paths.dem_root)?;
    validate_path(&paths.staging_root)?;
    validate_path(&paths.temp_root)?;
    Ok(())
}

#[uniffi::export]
pub fn core_api_version() -> u32 {
    CORE_API_VERSION
}

#[uniffi::export]
pub fn uniffi_bindings_version() -> u32 {
    UNIFFI_BINDINGS_VERSION
}

#[derive(uniffi::Object)]
pub struct NofarCore {
    mode: CoreMode,
}

#[uniffi::export]
impl NofarCore {
    #[uniffi::constructor]
    pub fn new(paths: CorePaths, mode: CoreMode) -> Result<Arc<Self>, NofarError> {
        validate_core_paths(&paths)?;
        if matches!(mode, CoreMode::Persistence) {
            return Err(NofarError::Internal);
        }
        Ok(Arc::new(Self { mode }))
    }

    pub fn core_api_version(&self) -> u32 {
        CORE_API_VERSION
    }

    pub fn database_schema_version(&self) -> u32 {
        match self.mode {
            CoreMode::ComputeOnly => 0,
            CoreMode::Persistence => 0,
        }
    }

    pub fn supported_dem_format_versions(&self) -> Vec<u32> {
        vec![1]
    }

    pub fn database_revision(&self) -> Result<u64, NofarError> {
        Err(NofarError::Database)
    }

    pub fn changes_since(&self, _revision: u64) -> Result<DatabaseChanges, NofarError> {
        Err(NofarError::Database)
    }
}

#[derive(uniffi::Object)]
pub struct CancellationToken {
    cancelled: AtomicBool,
}

#[uniffi::export]
impl CancellationToken {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            cancelled: AtomicBool::new(false),
        })
    }

    pub fn cancel(&self) {
        self.cancelled.store(true, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_api_version_is_stable() {
        assert_eq!(core_api_version(), nofar_core::CORE_API_VERSION);
        assert_eq!(
            uniffi_bindings_version(),
            nofar_core::UNIFFI_BINDINGS_VERSION
        );
    }

    #[test]
    fn open_compute_only_validates_paths() -> Result<(), String> {
        let paths = CorePaths {
            database_file: "/tmp/nofar.db".to_string(),
            dem_root: "/tmp/dem".to_string(),
            staging_root: "/tmp/staging".to_string(),
            temp_root: "/tmp/temp".to_string(),
        };
        let core = NofarCore::new(paths, CoreMode::ComputeOnly).map_err(|e| e.to_string())?;
        assert_eq!(core.core_api_version(), 1);
        assert!(core.database_revision().is_err());
        Ok(())
    }
}
