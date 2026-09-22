import sys, urllib.request, zipfile, pathlib
url=sys.argv[1]; dest=pathlib.Path(sys.argv[2]); dest.mkdir(parents=True,exist_ok=True)
z=dest/'devkit.zip'; urllib.request.urlretrieve(url,z)
with zipfile.ZipFile(z) as f: f.extractall(dest)
z.unlink()
