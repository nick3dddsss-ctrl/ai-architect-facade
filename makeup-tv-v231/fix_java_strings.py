from pathlib import Path
p=Path('/tmp/makeup231/app/src/main/java/ru/daniafedorina/makeuptv/MainActivity.java')
s=p.read_text()
s=s.replace('TextView h=text("Уроки макияжа\nна каждый день"','TextView h=text("Уроки макияжа\\nна каждый день"')
s=s.replace('TextView right=text("Dania\nFedorina"','TextView right=text("Dania\\nFedorina"')
p.write_text(s)
