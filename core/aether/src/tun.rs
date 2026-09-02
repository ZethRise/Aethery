#[cfg(unix)]
use std::fs::File;
#[cfg(unix)]
use std::io;
#[cfg(unix)]
use std::os::fd::{AsRawFd, FromRawFd};
#[cfg(unix)]
use std::sync::Mutex;

#[cfg(unix)]
use tokio::io::unix::AsyncFd;
use tokio::sync::mpsc;
#[cfg(unix)]
use tokio::sync::watch;

use crate::error::{AetherError, Result};
use crate::ffi;

#[cfg(unix)]
static TUN_FD_TX: Mutex<Option<watch::Sender<i32>>> = Mutex::new(None);

#[cfg(unix)]
pub fn replace_fd(fd: i32) {
    if let Some(tx) = TUN_FD_TX.lock().unwrap().as_ref() {
        let _ = tx.send(fd);
    }
}

#[cfg(not(unix))]
pub fn replace_fd(_fd: i32) {}

#[cfg(unix)]
fn open_tun(tun_fd: i32) -> io::Result<AsyncFd<File>> {
    let fd = unsafe { libc::dup(tun_fd) };
    if fd < 0 {
        return Err(io::Error::last_os_error());
    }
    let flags = unsafe { libc::fcntl(fd, libc::F_GETFL) };
    if flags < 0 || unsafe { libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK) } < 0 {
        let error = io::Error::last_os_error();
        unsafe { libc::close(fd) };
        return Err(error);
    }
    AsyncFd::new(unsafe { File::from_raw_fd(fd) })
}

/// Bridges Android's packet TUN file descriptor with Aether's raw-IP tunnel.
#[cfg(unix)]
pub async fn bridge(
    tun_fd: i32,
    mut inbound_rx: mpsc::Receiver<Vec<u8>>,
    outbound_tx: mpsc::Sender<Vec<u8>>,
) -> Result<()> {
    let (tx, mut rx) = watch::channel(tun_fd);
    *TUN_FD_TX.lock().unwrap() = Some(tx);
    let mut tun = open_tun(tun_fd).map_err(AetherError::Io)?;
    let mut packet = vec![0u8; 65_535];
    let mut rx_total = 0u64;
    let mut tx_total = 0u64;
    let mut last_emitted_tx = 0u64;
    let mut last_emitted_rx = 0u64;
    let mut last_report = std::time::Instant::now();

    loop {
        tokio::select! {
            changed = rx.changed() => {
                if changed.is_err() {
                    return Ok(());
                }
                let fd = *rx.borrow_and_update();
                if fd >= 0 {
                    tun = open_tun(fd).map_err(AetherError::Io)?;
                }
            }
            tunnel_packet = inbound_rx.recv() => match tunnel_packet {
                Some(packet) => {
                    rx_total += packet.len() as u64;
                    write_packet(&tun, &packet).await?;
                },
                None => return Ok(()),
            },
            read = read_packet(&tun, &mut packet) => {
                let length = read?;
                if length == 0 {
                    return Ok(());
                }
                tx_total += length as u64;
                outbound_tx.send(packet[..length].to_vec()).await
                    .map_err(|_| AetherError::Other("tunnel outbound channel closed".into()))?;
            },
        }

        if last_report.elapsed() >= std::time::Duration::from_millis(3000)
            && (tx_total != last_emitted_tx || rx_total != last_emitted_rx)
        {
            ffi::emit_traffic(tx_total, rx_total);
            last_emitted_tx = tx_total;
            last_emitted_rx = rx_total;
            last_report = std::time::Instant::now();
        }
    }
}

#[cfg(unix)]
async fn read_packet(tun: &AsyncFd<File>, packet: &mut [u8]) -> io::Result<usize> {
    loop {
        let mut ready = tun.readable().await?;
        match ready.try_io(|file| {
            let length = unsafe {
                libc::read(
                    file.get_ref().as_raw_fd(),
                    packet.as_mut_ptr().cast(),
                    packet.len(),
                )
            };
            if length < 0 {
                Err(io::Error::last_os_error())
            } else {
                Ok(length as usize)
            }
        }) {
            Ok(result) => return result,
            Err(_) => continue,
        }
    }
}

#[cfg(unix)]
async fn write_packet(tun: &AsyncFd<File>, packet: &[u8]) -> io::Result<()> {
    loop {
        let mut ready = tun.writable().await?;
        match ready.try_io(|file| {
            let length = unsafe {
                libc::write(
                    file.get_ref().as_raw_fd(),
                    packet.as_ptr().cast(),
                    packet.len(),
                )
            };
            if length < 0 {
                Err(io::Error::last_os_error())
            } else {
                Ok(length as usize)
            }
        }) {
            Ok(Ok(length)) if length == packet.len() => return Ok(()),
            Ok(Ok(_)) => {
                // A TUN write is one IP packet. A short write must not be
                // continued or the remainder becomes a second, corrupt frame.
                return Err(io::Error::new(
                    io::ErrorKind::WriteZero,
                    "TUN accepted only part of the packet",
                ));
            }
            Ok(Err(error)) => return Err(error),
            Err(_) => continue,
        }
    }
}

#[cfg(not(unix))]
pub async fn bridge(
    _tun_fd: i32,
    _inbound_rx: mpsc::Receiver<Vec<u8>>,
    _outbound_tx: mpsc::Sender<Vec<u8>>,
) -> Result<()> {
    Err(AetherError::Other(
        "TUN mode requires a Unix platform".into(),
    ))
}
