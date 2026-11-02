"""Package only application sources/configuration; never local data or credentials."""
from pathlib import Path
import tarfile

code_dir = Path(__file__).resolve().parent
backend_dir = code_dir / 'backend'
out = backend_dir / 'target/wastelens-embarko.tar.gz'
out.parent.mkdir(parents=True, exist_ok=True)

# Files from backend root to place at archive root
files = []
for p in ['pom.xml', 'railpack.json', 'package.json', 'mvnw', 'mvnw.cmd', 'embarko.json']:
    f = backend_dir / p
    if f.exists():
        files.append(f)

# All files in src/main
files += [p for p in (backend_dir / 'src/main').rglob('*') if p.is_file()]

# All files in .mvn
if (backend_dir / '.mvn').exists():
    files += [p for p in (backend_dir / '.mvn').rglob('*') if p.is_file()]

with tarfile.open(out, 'w:gz') as archive:
    for file in sorted(files):
        if file.is_file():
            arcname = file.relative_to(backend_dir).as_posix()
            info = archive.gettarinfo(str(file), arcname=arcname)
            if file.name == 'mvnw':
                info.mode = 0o755
            with file.open('rb') as content:
                archive.addfile(info, content)

print(f"Created source-only archive: {out.name} ({out.stat().st_size} bytes, {len(files)} files)")
