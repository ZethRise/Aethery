#[tokio::main]
async fn main() -> aether::error::Result<()> {
    aether::run_cli().await
}
