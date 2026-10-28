//! The VPN mode of Tor and Psiphon alone. Those two have no tunnel under them, only a SOCKS5
//! proxy, while the Android VPN interface hands over raw IP packets. This reads the packets,
//! ends their TCP connections in a userspace stack, and sends each one through that proxy; the
//! DNS questions go the same way over TCP. Tor and Psiphon carry TCP alone, so any other UDP
//! cannot leave and is dropped.

use std::net::SocketAddr;
use std::pin::Pin;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::task::{Context, Poll};
use std::time::Duration;

use ipstack::{IpStack, IpStackConfig, IpStackStream, IpStackTcpStream, IpStackUdpStream};
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt, ReadBuf};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, watch, Notify};

use crate::error::{AetherError, Result};

/// The SOCKS5 proxy every connection is sent through, and the login it asks for, if any.
#[derive(Debug, Clone)]
pub struct Upstream {
    pub addr: SocketAddr,
    pub login: Option<(String, String)>,
}

/// The packets of the VPN interface as one byte stream the stack can read and write: each read
/// returns a packet the device sent, each write is one packet for the device.
pub struct PacketDevice {
    from_tun: mpsc::Receiver<Vec<u8>>,
    to_tun: mpsc::Sender<Vec<u8>>,
}

impl AsyncRead for PacketDevice {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<std::io::Result<()>> {
        match self.from_tun.poll_recv(cx) {
            Poll::Ready(Some(packet)) => {
                // The stack's buffer is a whole packet long; a longer one is not one the interface made.
                let take = packet.len().min(buf.remaining());
                buf.put_slice(&packet[..take]);
                Poll::Ready(Ok(()))
            }
            // The interface closed: the end of the stream.
            Poll::Ready(None) => Poll::Ready(Ok(())),
            Poll::Pending => Poll::Pending,
        }
    }
}

impl AsyncWrite for PacketDevice {
    fn poll_write(self: Pin<&mut Self>, _cx: &mut Context<'_>, buf: &[u8]) -> Poll<std::io::Result<usize>> {
        match self.to_tun.try_send(buf.to_vec()) {
            Ok(()) => Poll::Ready(Ok(buf.len())),
            // A full queue is a congested link: the packet is lost and the connection resends it.
            Err(mpsc::error::TrySendError::Full(_)) => Poll::Ready(Ok(buf.len())),
            Err(mpsc::error::TrySendError::Closed(_)) => Poll::Ready(Err(std::io::ErrorKind::BrokenPipe.into())),
        }
    }
    fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Poll::Ready(Ok(()))
    }
    fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Poll::Ready(Ok(()))
    }
}

fn other(message: impl Into<String>) -> std::io::Error {
    std::io::Error::other(message.into())
}

/// Asks the SOCKS5 proxy on `stream` to connect to `destination`.
pub async fn socks5_connect<S>(stream: &mut S, destination: SocketAddr, login: Option<&(String, String)>) -> std::io::Result<()>
where
    S: AsyncRead + AsyncWrite + Unpin,
{
    // One method is offered: the login when there is one, nothing otherwise.
    let method = if login.is_some() { 0x02 } else { 0x00 };
    stream.write_all(&[5, 1, method]).await?;
    let mut chosen = [0u8; 2];
    stream.read_exact(&mut chosen).await?;
    if chosen[0] != 5 || chosen[1] != method {
        return Err(other("the proxy does not take the login method asked of it"));
    }
    if let Some((user, pass)) = login {
        let (user, pass) = (user.as_bytes(), pass.as_bytes());
        if user.len() > 255 || pass.len() > 255 {
            return Err(other("the proxy login is too long"));
        }
        let mut message = vec![1, user.len() as u8];
        message.extend_from_slice(user);
        message.push(pass.len() as u8);
        message.extend_from_slice(pass);
        stream.write_all(&message).await?;
        let mut status = [0u8; 2];
        stream.read_exact(&mut status).await?;
        if status[1] != 0 {
            return Err(other("the proxy turned the login down"));
        }
    }

    let mut request = vec![5, 1, 0];
    match destination.ip() {
        std::net::IpAddr::V4(ip) => {
            request.push(1);
            request.extend_from_slice(&ip.octets());
        }
        std::net::IpAddr::V6(ip) => {
            request.push(4);
            request.extend_from_slice(&ip.octets());
        }
    }
    request.extend_from_slice(&destination.port().to_be_bytes());
    stream.write_all(&request).await?;

    let mut head = [0u8; 4];
    stream.read_exact(&mut head).await?;
    if head[1] != 0 {
        return Err(other(format!("the proxy refused the connection to {destination} (reply {})", head[1])));
    }
    // The address the proxy bound is of no use; it is read to leave the stream at the data.
    let skip = match head[3] {
        1 => 4 + 2,
        4 => 16 + 2,
        3 => {
            let mut length = [0u8; 1];
            stream.read_exact(&mut length).await?;
            length[0] as usize + 2
        }
        _ => return Err(other("the proxy answered with an address of a kind it does not have")),
    };
    let mut rest = vec![0u8; skip];
    stream.read_exact(&mut rest).await?;
    Ok(())
}

/// A DNS message framed for a TCP stream: its length in two bytes, then the message.
pub fn frame_dns(message: &[u8]) -> Vec<u8> {
    let mut framed = Vec::with_capacity(message.len() + 2);
    framed.extend_from_slice(&(message.len() as u16).to_be_bytes());
    framed.extend_from_slice(message);
    framed
}

/// Whether a datagram to `destination` is a DNS question this carries (over TCP, through the proxy).
pub fn is_dns(destination: SocketAddr) -> bool {
    destination.port() == 53
}

const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const DNS_TIMEOUT: Duration = Duration::from_secs(10);
const FALLBACK_RESOLVER: SocketAddr = SocketAddr::new(std::net::IpAddr::V4(std::net::Ipv4Addr::new(1, 1, 1, 1)), 53);

async fn connect_through(upstream: &Upstream, destination: SocketAddr) -> std::io::Result<TcpStream> {
    let attempt = async {
        let mut stream = TcpStream::connect(upstream.addr).await?;
        socks5_connect(&mut stream, destination, upstream.login.as_ref()).await?;
        Ok::<_, std::io::Error>(stream)
    };
    tokio::time::timeout(CONNECT_TIMEOUT, attempt)
        .await
        .map_err(|_| other(format!("the proxy did not connect to {destination} in time")))?
}

/// One DNS question, asked of `resolver` over TCP through the proxy.
async fn ask_resolver(upstream: &Upstream, resolver: SocketAddr, query: &[u8]) -> std::io::Result<Vec<u8>> {
    let exchange = async {
        let mut stream = connect_through(upstream, resolver).await?;
        stream.write_all(&frame_dns(query)).await?;
        let mut length = [0u8; 2];
        stream.read_exact(&mut length).await?;
        let mut answer = vec![0u8; u16::from_be_bytes(length) as usize];
        stream.read_exact(&mut answer).await?;
        Ok::<_, std::io::Error>(answer)
    };
    tokio::time::timeout(DNS_TIMEOUT, exchange)
        .await
        .map_err(|_| other("the resolver did not answer in time"))?
}

/// How many connection handlers are running. The userspace stack's streams wait for their own
/// tasks when they are dropped, which hangs if that happens while the runtime is shutting down, so
/// each handler ends itself, in a live runtime, when `Teardown` goes, and the caller waits for
/// that with [`settle`] before the runtime is dropped.
static ACTIVE: AtomicUsize = AtomicUsize::new(0);
static SETTLED: Notify = Notify::const_new();

struct Active;

impl Active {
    fn begin() -> Self {
        ACTIVE.fetch_add(1, Ordering::SeqCst);
        Active
    }
}

impl Drop for Active {
    fn drop(&mut self) {
        ACTIVE.fetch_sub(1, Ordering::SeqCst);
        SETTLED.notify_waiters();
    }
}

/// Waits, for a few seconds at most, for every connection handler to have ended.
pub async fn settle() {
    let wait = async {
        loop {
            let ended = SETTLED.notified();
            if ACTIVE.load(Ordering::SeqCst) == 0 {
                return;
            }
            ended.await;
        }
    };
    let _ = tokio::time::timeout(Duration::from_secs(5), wait).await;
}

/// Ends the handlers it was given receivers of when it is dropped.
struct Teardown(#[allow(dead_code)] watch::Sender<()>);

/// Ends a task that is aborted when this is dropped, so the bridge does not outlive the run.
struct AbortOnDrop<T>(tokio::task::JoinHandle<T>);

impl<T> Drop for AbortOnDrop<T> {
    fn drop(&mut self) {
        self.0.abort();
    }
}

async fn proxy_tcp(mut client: IpStackTcpStream, upstream: Arc<Upstream>, mut done: watch::Receiver<()>, _active: Active) {
    let destination = client.peer_addr();
    let carried = async {
        match connect_through(&upstream, destination).await {
            Ok(mut remote) => {
                let _ = tokio::io::copy_bidirectional(&mut client, &mut remote).await;
            }
            Err(error) => log::debug!("[-] {destination} through the proxy: {error}"),
        }
    };
    tokio::select! {
        _ = carried => {}
        _ = done.changed() => {}
    }
    // The stream is let go here, in the runtime, not when the runtime is torn down.
    drop(client);
}

async fn proxy_dns(mut client: IpStackUdpStream, upstream: Arc<Upstream>, mut done: watch::Receiver<()>, _active: Active) {
    let resolver = client.peer_addr();
    let carried = async {
        let mut datagram = vec![0u8; 4096];
        loop {
            let read = match tokio::time::timeout(Duration::from_secs(20), client.read(&mut datagram)).await {
                Ok(Ok(read)) if read > 0 => read,
                _ => return,
            };
            let query = &datagram[..read];
            let answer = match ask_resolver(&upstream, resolver, query).await {
                Ok(answer) => Ok(answer),
                Err(first) if resolver != FALLBACK_RESOLVER => {
                    log::debug!("[-] dns via {resolver}: {first}; trying {FALLBACK_RESOLVER}");
                    ask_resolver(&upstream, FALLBACK_RESOLVER, query).await
                }
                Err(error) => Err(error),
            };
            match answer {
                Ok(answer) => {
                    if client.write_all(&answer).await.is_err() {
                        return;
                    }
                }
                Err(error) => log::debug!("[-] dns through the proxy: {error}"),
            }
        }
    };
    tokio::select! {
        _ = carried => {}
        _ = done.changed() => {}
    }
}

/// Carries the packets of the VPN interface `tun_fd` through `upstream` until either ends.
pub async fn run(tun_fd: i32, upstream: Upstream, mtu: u16) -> Result<()> {
    let capacity = crate::sysprofile::channel_capacity();
    let (to_tun, to_tun_rx) = mpsc::channel(capacity);
    let (from_tun_tx, from_tun) = mpsc::channel(capacity);
    let mut bridge = AbortOnDrop(tokio::spawn(crate::tun::bridge(tun_fd, to_tun_rx, from_tun_tx)));
    let carrying = carry(PacketDevice { from_tun, to_tun }, upstream, mtu);
    tokio::pin!(carrying);
    tokio::select! {
        result = &mut carrying => result,
        ended = &mut bridge.0 => match ended {
            Ok(result) => result,
            Err(error) => Err(AetherError::Other(format!("the VPN interface bridge stopped: {error}"))),
        },
    }
}

/// Ends the connections of the packets on `device` and sends each through `upstream`.
async fn carry<D>(device: D, upstream: Upstream, mtu: u16) -> Result<()>
where
    D: AsyncRead + AsyncWrite + Unpin + Send + 'static,
{
    let mut config = IpStackConfig::default();
    config
        .mtu(mtu)
        .map_err(|error| AetherError::Other(format!("the VPN interface MTU is not usable: {error}")))?
        .udp_timeout(Duration::from_secs(20));
    let mut stack = IpStack::new(config, device);
    let upstream = Arc::new(upstream);
    let (done_tx, done_rx) = watch::channel(());
    let _teardown = Teardown(done_tx);
    let mut dropped_udp = false;
    crate::ffi::record_log("Carrying the VPN interface through the proxy");

    loop {
        let accepted = stack
            .accept()
            .await
            .map_err(|error| AetherError::Other(format!("the VPN interface stack stopped: {error}")))?;
        match accepted {
            IpStackStream::Tcp(tcp) => {
                tokio::spawn(proxy_tcp(tcp, upstream.clone(), done_rx.clone(), Active::begin()));
            }
            IpStackStream::Udp(udp) if is_dns(udp.peer_addr()) => {
                tokio::spawn(proxy_dns(udp, upstream.clone(), done_rx.clone(), Active::begin()));
            }
            IpStackStream::Udp(udp) => {
                if !dropped_udp {
                    dropped_udp = true;
                    crate::ffi::record_log(format!(
                        "The proxy carries TCP only, so UDP such as {} is dropped",
                        udp.peer_addr()
                    ));
                }
            }
            IpStackStream::UnknownTransport(_) | IpStackStream::UnknownNetwork(_) => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
    use tokio::io::duplex;

    fn v4(a: u8, b: u8, c: u8, d: u8, port: u16) -> SocketAddr {
        SocketAddr::new(IpAddr::V4(Ipv4Addr::new(a, b, c, d)), port)
    }

    /// A proxy that answers `reply` to the request and gives back what the client sent.
    async fn run_proxy(mut server: tokio::io::DuplexStream, wants_login: bool, reply: [u8; 10]) -> Vec<u8> {
        let mut seen = Vec::new();
        let mut greeting = [0u8; 3];
        server.read_exact(&mut greeting).await.unwrap();
        seen.extend_from_slice(&greeting);
        if wants_login {
            // The client offered user/pass (0x02); read its login and accept it.
            server.write_all(&[5, 2]).await.unwrap();
            let mut head = [0u8; 2];
            server.read_exact(&mut head).await.unwrap();
            let mut user = vec![0u8; head[1] as usize];
            server.read_exact(&mut user).await.unwrap();
            let mut plen = [0u8; 1];
            server.read_exact(&mut plen).await.unwrap();
            let mut pass = vec![0u8; plen[0] as usize];
            server.read_exact(&mut pass).await.unwrap();
            seen.extend_from_slice(&head);
            seen.extend_from_slice(&user);
            seen.push(plen[0]);
            seen.extend_from_slice(&pass);
            server.write_all(&[1, 0]).await.unwrap();
        } else {
            server.write_all(&[5, 0]).await.unwrap();
        }
        let mut request = [0u8; 4];
        server.read_exact(&mut request).await.unwrap();
        seen.extend_from_slice(&request);
        let rest = match request[3] {
            1 => 6,
            4 => 18,
            other => panic!("unexpected address type {other}"),
        };
        let mut tail = vec![0u8; rest];
        server.read_exact(&mut tail).await.unwrap();
        seen.extend_from_slice(&tail);
        server.write_all(&reply).await.unwrap();
        seen
    }

    const OK: [u8; 10] = [5, 0, 0, 1, 0, 0, 0, 0, 0, 0];

    #[tokio::test]
    async fn connect_asks_the_proxy_for_the_ipv4_destination() {
        let (mut client, server) = duplex(256);
        let proxy = tokio::spawn(run_proxy(server, false, OK));
        socks5_connect(&mut client, v4(93, 184, 216, 34, 443), None).await.unwrap();
        let seen = proxy.await.unwrap();
        assert_eq!(seen, vec![5, 1, 0, /*request*/ 5, 1, 0, 1, 93, 184, 216, 34, 0x01, 0xbb]);
    }

    #[tokio::test]
    async fn connect_asks_the_proxy_for_an_ipv6_destination() {
        let (mut client, server) = duplex(256);
        let proxy = tokio::spawn(run_proxy(server, false, OK));
        let destination = SocketAddr::new(IpAddr::V6(Ipv6Addr::LOCALHOST), 80);
        socks5_connect(&mut client, destination, None).await.unwrap();
        let seen = proxy.await.unwrap();
        assert_eq!(&seen[3..7], &[5, 1, 0, 4]);
        assert_eq!(&seen[7..23], &Ipv6Addr::LOCALHOST.octets());
        assert_eq!(&seen[23..], &[0, 80]);
    }

    #[tokio::test]
    async fn connect_logs_in_when_the_proxy_wants_a_login() {
        let (mut client, server) = duplex(256);
        let proxy = tokio::spawn(run_proxy(server, true, OK));
        let login = ("aethery".to_string(), "pw".to_string());
        socks5_connect(&mut client, v4(1, 1, 1, 1, 53), Some(&login)).await.unwrap();
        let seen = proxy.await.unwrap();
        // With a login the greeting offers the user/pass method alone.
        assert_eq!(&seen[..3], &[5, 1, 2]);
    }

    #[tokio::test]
    async fn a_refusal_from_the_proxy_is_an_error() {
        let (mut client, server) = duplex(256);
        let refused = [5, 5, 0, 1, 0, 0, 0, 0, 0, 0];
        tokio::spawn(run_proxy(server, false, refused));
        let error = socks5_connect(&mut client, v4(10, 0, 0, 1, 80), None).await.unwrap_err();
        assert!(error.to_string().contains("refused"), "{error}");
    }

    #[test]
    fn a_dns_message_is_framed_with_its_length() {
        assert_eq!(frame_dns(&[1, 2, 3]), vec![0, 3, 1, 2, 3]);
        assert_eq!(frame_dns(&[]), vec![0, 0]);
    }

    #[test]
    fn only_port_53_is_dns() {
        assert!(is_dns(v4(1, 1, 1, 1, 53)));
        assert!(is_dns(SocketAddr::new(IpAddr::V6(Ipv6Addr::LOCALHOST), 53)));
        assert!(!is_dns(v4(1, 1, 1, 1, 443)));
        assert!(!is_dns(v4(1, 1, 1, 1, 5353)));
    }

    #[tokio::test]
    async fn the_device_reads_the_packets_the_tun_sent_and_writes_one_packet_per_write() {
        let (tun_tx, from_tun) = mpsc::channel(4);
        let (to_tun, mut tun_rx) = mpsc::channel(4);
        let mut device = PacketDevice { from_tun, to_tun };
        tun_tx.send(vec![1, 2, 3, 4]).await.unwrap();
        tun_tx.send(vec![9, 9]).await.unwrap();
        let mut buffer = [0u8; 64];
        assert_eq!(device.read(&mut buffer).await.unwrap(), 4);
        assert_eq!(&buffer[..4], &[1, 2, 3, 4]);
        assert_eq!(device.read(&mut buffer).await.unwrap(), 2, "one packet per read");
        device.write_all(&[7, 7, 7]).await.unwrap();
        assert_eq!(tun_rx.recv().await.unwrap(), vec![7, 7, 7]);
        drop(tun_tx);
        assert_eq!(device.read(&mut buffer).await.unwrap(), 0, "the end of the tun is the end of the stream");
    }

    /// A tcp connection from the VPN interface comes out of the proxy as a CONNECT to the address it
    /// was made to, with its data after it.
    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn a_connection_from_the_vpn_interface_is_sent_through_the_proxy() {
        use etherparse::PacketBuilder;

        let proxy = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let proxy_addr = proxy.local_addr().unwrap();
        let proxy_task = tokio::spawn(async move {
            let (mut connection, _) = proxy.accept().await.unwrap();
            let mut greeting = [0u8; 3];
            connection.read_exact(&mut greeting).await.unwrap();
            connection.write_all(&[5, 0]).await.unwrap();
            let mut request = [0u8; 10];
            connection.read_exact(&mut request).await.unwrap();
            connection.write_all(&OK).await.unwrap();
            let mut data = [0u8; 4];
            connection.read_exact(&mut data).await.unwrap();
            (request, data)
        });

        let (tun_tx, from_tun) = mpsc::channel(16);
        let (to_tun, mut tun_rx) = mpsc::channel(16);
        let carrying = tokio::spawn(carry(PacketDevice { from_tun, to_tun }, Upstream { addr: proxy_addr, login: None }, 1280));

        let client = [10, 0, 0, 2];
        let server = [93, 184, 216, 34];
        let syn = {
            let mut bytes = Vec::new();
            PacketBuilder::ipv4(client, server, 64).tcp(40000, 80, 1000, 65535).syn().write(&mut bytes, &[]).unwrap();
            bytes
        };
        tun_tx.send(syn).await.unwrap();

        let reply = tokio::time::timeout(Duration::from_secs(5), tun_rx.recv()).await.unwrap().unwrap();
        let sliced = etherparse::SlicedPacket::from_ip(&reply).unwrap();
        let tcp = match sliced.transport {
            Some(etherparse::TransportSlice::Tcp(tcp)) => tcp,
            other => panic!("expected a tcp reply, got {other:?}"),
        };
        assert!(tcp.syn() && tcp.ack(), "expected a SYN-ACK");
        assert_eq!(tcp.acknowledgment_number(), 1001);
        let server_seq = tcp.sequence_number();

        let ack = {
            let mut bytes = Vec::new();
            PacketBuilder::ipv4(client, server, 64)
                .tcp(40000, 80, 1001, 65535)
                .ack(server_seq + 1)
                .write(&mut bytes, &[])
                .unwrap();
            bytes
        };
        tun_tx.send(ack).await.unwrap();
        let data = {
            let mut bytes = Vec::new();
            PacketBuilder::ipv4(client, server, 64)
                .tcp(40000, 80, 1001, 65535)
                .psh()
                .ack(server_seq + 1)
                .write(&mut bytes, b"ping")
                .unwrap();
            bytes
        };
        tun_tx.send(data).await.unwrap();

        let (request, data) = tokio::time::timeout(Duration::from_secs(5), proxy_task).await.unwrap().unwrap();
        assert_eq!(request, [5, 1, 0, 1, 93, 184, 216, 34, 0, 80], "CONNECT to the address the connection was made to");
        assert_eq!(&data, b"ping");
        carrying.abort();
        settle().await;
        assert_eq!(ACTIVE.load(Ordering::SeqCst), 0, "every handler ended");
    }

    /// The way the core stops: the run is dropped while a connection is open, then the runtime is
    /// let go. That must not hang on the stack's streams.
    #[test]
    fn stopping_with_a_connection_open_lets_the_runtime_go() {
        use etherparse::PacketBuilder;
        let (finished, wait) = std::sync::mpsc::channel();
        std::thread::spawn(move || {
            let runtime = tokio::runtime::Builder::new_multi_thread().worker_threads(2).enable_all().build().unwrap();
            runtime.block_on(async {
                let proxy = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
                let proxy_addr = proxy.local_addr().unwrap();
                let (connected, connected_rx) = tokio::sync::oneshot::channel::<()>();
                tokio::spawn(async move {
                    let (mut connection, _) = proxy.accept().await.unwrap();
                    let mut greeting = [0u8; 3];
                    connection.read_exact(&mut greeting).await.unwrap();
                    connection.write_all(&[5, 0]).await.unwrap();
                    let mut request = [0u8; 10];
                    connection.read_exact(&mut request).await.unwrap();
                    connection.write_all(&OK).await.unwrap();
                    let _ = connected.send(());
                    // The connection stays open until the other end goes.
                    let mut sink = Vec::new();
                    let _ = connection.read_to_end(&mut sink).await;
                });

                let (tun_tx, from_tun) = mpsc::channel(16);
                let (to_tun, mut tun_rx) = mpsc::channel(16);
                let client = [10, 0, 0, 2];
                let server = [93, 184, 216, 34];
                let mut syn = Vec::new();
                PacketBuilder::ipv4(client, server, 64).tcp(40001, 80, 1000, 65535).syn().write(&mut syn, &[]).unwrap();
                tun_tx.send(syn).await.unwrap();
                tokio::select! {
                    _ = carry(PacketDevice { from_tun, to_tun }, Upstream { addr: proxy_addr, login: None }, 1280) => panic!("the run ended on its own"),
                    _ = async {
                        let reply = tun_rx.recv().await.unwrap();
                        let sliced = etherparse::SlicedPacket::from_ip(&reply).unwrap();
                        let server_seq = match sliced.transport {
                            Some(etherparse::TransportSlice::Tcp(tcp)) => tcp.sequence_number(),
                            other => panic!("expected a tcp reply, got {other:?}"),
                        };
                        let mut ack = Vec::new();
                        PacketBuilder::ipv4(client, server, 64).tcp(40001, 80, 1001, 65535).ack(server_seq + 1).write(&mut ack, &[]).unwrap();
                        tun_tx.send(ack).await.unwrap();
                        connected_rx.await.unwrap();
                    } => {}
                }
                // Out here the run has been dropped, as the core does when it is told to stop.
            });
            runtime.block_on(settle());
            drop(runtime);
            let _ = finished.send(());
        });
        wait.recv_timeout(Duration::from_secs(20)).expect("the runtime did not shut down");
    }
}
