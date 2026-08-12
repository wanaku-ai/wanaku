fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_prost_build::configure()
        .build_server(false)
        .build_client(true)
        .compile_protos(
            &["src/proto/toolrequest.proto", "src/proto/resourcerequest.proto"],
            &["src/proto"],
        )?;
    Ok(())
}
