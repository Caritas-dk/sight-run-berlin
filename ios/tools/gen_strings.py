"""Genererer ios/BerlinSightrun/I18n/UIStrings.swift fra Android-appens Strings.kt,
så de faste tekster er ens på Android og iOS.

    python3 ios/tools/gen_strings.py          # skriv filen
    python3 ios/tools/gen_strings.py --check  # fejl, hvis filen ikke er opdateret (bruges i CI)
"""
import re,sys,os
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC=os.path.join(ROOT,'app/src/main/java/dk/berlinsightrun/i18n/Strings.kt')
OUT=os.path.join(ROOT,'ios/BerlinSightrun/I18n/UIStrings.swift')
src=open(SRC,encoding='utf-8').read()
objs={}
for name,code in [("Danish","da"),("German","de"),("English","en"),("French","fr")]:
    start=src.index(f"object {name} : Strings()")
    end=src.find("\nobject ",start+10); end=len(src) if end<0 else end
    body=src[start:end]
    vals=dict(re.findall(r'override val (\w+) = (".*?")\n',body))
    vals={k:v for k,v in vals.items()}
    dec=re.search(r"decimalSeparator = '(.)'",body).group(1)
    objs[code]=(vals,dec)
keys=list(objs["da"][0].keys())
for c,(v,_) in objs.items():
    assert set(v)==set(keys),(c,set(keys)^set(v))
def sw(s): # kotlin literal -> swift literal (no templates in vals)
    assert "$" not in s, s
    return s
out=["// GENERERET fra app/src/main/java/dk/berlinsightrun/i18n/Strings.kt af ios/tools/gen_strings.py.","// Ret teksterne i Kotlin-filen, og kør scriptet igen, så Android og iOS siger det samme.","","struct UIStrings {","    let decimalSeparator: Character"]
out+= [f"    let {k}: String" for k in keys]
out.append("}\n\nextension UIStrings {")
for c,(v,dec) in objs.items():
    out.append(f"    static let {c} = UIStrings(\n        decimalSeparator: \"{dec}\",")
    out.append(",\n".join(f"        {k}: {sw(v[k])}" for k in keys))
    out.append("    )\n")
out.append("}\n")
text="\n".join(out)
if "--check" in sys.argv:
    if open(OUT,encoding='utf-8').read()!=text:
        sys.exit("UIStrings.swift passer ikke med Strings.kt. Kør: python3 ios/tools/gen_strings.py")
    print("UIStrings.swift er opdateret")
else:
    open(OUT,'w',encoding='utf-8').write(text)
