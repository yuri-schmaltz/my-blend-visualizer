import argparse
import bpy
import pathlib
import sys


def parse_args() -> argparse.Namespace:
    if "--" not in sys.argv:
        raise RuntimeError("Argumentos nao encontrados. Use -- --output <arquivo.glb>.")

    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    return parser.parse_args(sys.argv[sys.argv.index("--") + 1 :])


def main() -> None:
    args = parse_args()
    output_path = pathlib.Path(args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    bpy.ops.export_scene.gltf(
        filepath=str(output_path),
        export_format="GLB",
        export_yup=True,
        export_apply=True,
    )


if __name__ == "__main__":
    main()
