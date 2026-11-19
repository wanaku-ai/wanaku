//! Wall-clock interruption worker shared by synchronous WASM runtimes.

use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::thread::JoinHandle;
use std::time::Duration;

/// Advances engine epochs while an invocation is active, including when the
/// async request is cancelled. Pair this with a wall-clock deadline callback:
/// concurrent invocations can advance the same engine more than once per tick.
pub struct EpochTicker {
    stop: Sender<()>,
    worker: Option<JoinHandle<()>>,
}

impl EpochTicker {
    pub fn start(tick: impl Fn() + Send + 'static) -> std::io::Result<Self> {
        let (stop, receiver) = mpsc::channel();
        let worker = std::thread::Builder::new()
            .name("wasm-execution-budget".to_owned())
            .spawn(move || {
                while matches!(
                    receiver.recv_timeout(Duration::from_millis(10)),
                    Err(RecvTimeoutError::Timeout)
                ) {
                    tick();
                }
            })?;
        Ok(Self {
            stop,
            worker: Some(worker),
        })
    }
}

impl Drop for EpochTicker {
    fn drop(&mut self) {
        let _ = self.stop.send(());
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}
