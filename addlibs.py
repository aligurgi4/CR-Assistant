import sys, zipfile, os

"""
Adds the native .so files to an APK.

Native libraries MUST be stored uncompressed (compress_type=ZIP_STORED),
otherwise Android cannot load them with System.loadLibrary() and the app
silently loses its voices and other native features.
"""
apk = sys.argv[1]
libdir = sys.argv[2]

z = zipfile.ZipFile(apk, "a", zipfile.ZIP_STORED)
for root, _, fs in os.walk(libdir):
    for f in fs:
        p = os.path.join(root, f)
        arc = p
        info = zipfile.ZipInfo(arc, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        info.external_attr = 0o100755 << 16
        with open(p, "rb") as fh:
            z.writestr(info, fh.read())
z.close()
