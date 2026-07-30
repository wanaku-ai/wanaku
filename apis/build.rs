fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure()
        .build_server(false)
        .build_client(true)
        .compile_protos(&["src/proto/toolrequest.proto"], &["src/proto"])?;
    Ok(())
}
