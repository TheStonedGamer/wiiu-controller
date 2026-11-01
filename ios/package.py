from pathlib import Path
import zipfile
import os, subprocess
from PIL import Image,ImageDraw
root=Path('build/Payload/WiiUController.app')
icon=Image.new('RGB',(120,120),'#101827');d=ImageDraw.Draw(icon);d.rounded_rectangle((18,30,102,90),radius=16,fill='#5addc5');d.rectangle((30,54,62,64),fill='#101827');d.rectangle((41,43,51,75),fill='#101827');d.ellipse((79,46,91,58),fill='#101827');d.ellipse((72,65,84,77),fill='#101827');icon.save(root/'AppIcon60x60@2x.png')
Image.new('RGB',(640,1136),'#101827').save(root/'Default-568h@2x.png')
subprocess.run([os.environ.get('LDID', 'ldid'), '-SEntitlements.plist', str(root)], check=True)
with zipfile.ZipFile('../dist/WiiU-Controller-iPhone5s-JAILBREAK.ipa','w',zipfile.ZIP_DEFLATED) as ipa:
 for p in sorted(root.rglob('*')):
  if p.is_file():
   info=zipfile.ZipInfo(str(p.relative_to('build')));info.external_attr=(0o100755 if p.name=='WiiUController' else 0o100644)<<16;info.compress_type=zipfile.ZIP_DEFLATED;ipa.writestr(info,p.read_bytes())
print('Built ad-hoc signed arm64 iOS 12+ IPA with application identifier. Requires jailbreak/AppSync; not Apple provisioned.')
