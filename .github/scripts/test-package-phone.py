import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('packager', Path(__file__).with_name('package-phone.py'))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class PackagingTest(unittest.TestCase):
    def test_all_abis_are_preserved_and_arm64_is_named(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            elements = []
            for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'):
                name = 'app-' + abi + '.apk'
                (root/name).write_text(abi)
                elements.append({'filters':[{'filterType':'ABI','value':abi}],'outputFile':name})
            (root/'output-metadata.json').write_text(json.dumps({'elements':elements}))
            result = packager.package(root,root/'out','release','0.7.0','abc123')
            self.assertEqual(len(result),4)
            self.assertEqual(result['arm64-v8a'].read_text(),'arm64-v8a')
            elements[0]['filters'] = []
            (root/'output-metadata.json').write_text(json.dumps({'elements':elements}))
            with self.assertRaises(ValueError):
                packager.package(root,root/'out','release','0.7.0','abc123')


if __name__ == '__main__': unittest.main()
