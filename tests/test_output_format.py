#!/usr/bin/env python3
"""CLI integration tests; run after mvn package -DskipNative=true."""

import os
import pathlib
import struct
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR = ROOT / "target" / "image-to-array-1.4.jar"
IMAGE = ROOT / "src" / "main" / "resources" / "hearts2.png"


def run_tool(*args):
    return subprocess.run(
        ["java", "-Djava.awt.headless=true", "-jar", str(JAR), "-i", str(IMAGE), *map(str, args)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=ROOT,
        check=False,
    )


class OutputFormatTests(unittest.TestCase):
    def test_explicit_formats_to_stdout_and_mismatched_filename(self):
        signatures = {
            "html": b'<img id="ita_hearts2" src="data:image/png;base64,',
            "md": b"![ita_hearts2](data:image/png;base64,",
            "java": b"import java.awt.image.BufferedImage;",
            "png": b"\x89PNG\r\n\x1a\n",
            "bmp": b"BM",
            "gif": b"GIF8",
            "jpg": b"\xff\xd8\xff",
            "webp": b"RIFF",
            "ico": b"\x00\x00\x01\x00",
            "icns": b"icns",
        }
        with tempfile.TemporaryDirectory() as directory:
            decoder_source = pathlib.Path(directory) / "DecodeCheck.java"
            decoder_source.write_text(
                "import java.io.File;\n"
                "import javax.imageio.ImageIO;\n"
                "public class DecodeCheck {\n"
                "  public static void main(String[] args) throws Exception {\n"
                "    var image = ImageIO.read(new File(args[0]));\n"
                "    var source = ImageIO.read(new File(args[1]));\n"
                "    if (image == null || source == null || image.getWidth() != source.getWidth()\n"
                "        || image.getHeight() != source.getHeight())\n"
                "      throw new IllegalStateException(\"Invalid image dimensions or data\");\n"
                "  }\n"
                "}\n",
                encoding="utf-8",
            )
            compiled = subprocess.run(
                ["javac", str(decoder_source)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
            )
            self.assertEqual(compiled.returncode, 0, compiled.stderr.decode(errors="replace"))
            for format_name, signature in signatures.items():
                with self.subTest(format=format_name):
                    stdout = run_tool("-f", format_name.upper())
                    self.assertEqual(stdout.returncode, 0, stdout.stderr.decode(errors="replace"))
                    self.assertTrue(stdout.stdout.startswith(signature), stdout.stdout[:40])
                    if format_name == "webp":
                        self.assertEqual(stdout.stdout[8:12], b"WEBP")
                    destination = pathlib.Path(directory) / "output.dat"
                    file_result = run_tool("--output-format", format_name, "-o", destination)
                    self.assertEqual(file_result.returncode, 0, file_result.stderr.decode(errors="replace"))
                    self.assertEqual(file_result.stdout, b"")
                    content = destination.read_bytes()
                    self.assertTrue(content.startswith(signature), content[:40])
                    if format_name == "java":
                        self.assertIn(b"public class hearts2 extends BufferedImage", stdout.stdout)
                        self.assertIn(b"public class output_dat extends BufferedImage", content)
                        java_file = pathlib.Path(directory) / "hearts2.java"
                        java_file.write_bytes(stdout.stdout)
                        compiled = subprocess.run(
                            ["javac", str(java_file)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
                        )
                        self.assertEqual(compiled.returncode, 0, compiled.stderr.decode(errors="replace"))
                    else:
                        self.assertEqual(content, stdout.stdout)
                    if format_name in ("png", "bmp", "gif", "jpg", "webp"):
                        decoded = subprocess.run(
                            ["java", "-cp", f"{directory}{os.pathsep}{JAR}", "DecodeCheck", str(destination), str(IMAGE)],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
                        )
                        self.assertEqual(decoded.returncode, 0, decoded.stderr.decode(errors="replace"))
                    if format_name == "ico":
                        count = struct.unpack_from("<H", content, 4)[0]
                        self.assertEqual(count, 5)
                        for index in range(count):
                            size, offset = struct.unpack_from("<II", content, 6 + index * 16 + 8)
                            self.assertGreater(size, 0)
                            self.assertLessEqual(offset + size, len(content))
                    if format_name == "icns":
                        self.assertEqual(struct.unpack_from(">I", content, 4)[0], len(content))

    def test_text_output_and_legacy_behavior(self):
        legacy = run_tool("-c", "CRGB")
        explicit = run_tool("-c", "CRGB", "-f", "txt")
        self.assertEqual(legacy.returncode, 0, legacy.stderr)
        self.assertEqual(explicit.returncode, 0, explicit.stderr)
        self.assertEqual(explicit.stdout, legacy.stdout)
        self.assertIn(b"CRGB hearts2[", explicit.stdout)
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "pixels.png"
            result = run_tool("-c", "CRGB", "-f", "txt", "-o", path)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(b"CRGB hearts2[", path.read_bytes())
            png = pathlib.Path(directory) / "legacy.png"
            result = run_tool("-o", png)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(png.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"))

    def test_override_applies_to_multiple_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            first = pathlib.Path(directory) / "one.jpg"
            second = pathlib.Path(directory) / "two.gif"
            result = run_tool("-o", first, "-o", second, "-f", "png")
            self.assertEqual(result.returncode, 0, result.stderr)
            # Named outputs are paired by index with inputs; provide a second input.
            self.assertTrue(first.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"))
            self.assertFalse(second.exists())
            result = run_tool("-i", IMAGE, "-o", first, "-o", second, "-f", "png")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(second.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"))

    def test_invalid_and_incompatible_arguments_fail(self):
        for args in [
            ("-f", "tiff"),
            ("-f",),
            ("-f", "txt"),
            ("-f", "png", "-c", "CRGB"),
            ("-f", "png", "-i", IMAGE),
        ]:
            with self.subTest(args=args):
                result = run_tool(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b"")
                self.assertIn(b"error", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
