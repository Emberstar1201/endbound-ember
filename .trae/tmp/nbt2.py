import gzip, struct, collections

def load(p):
    data = gzip.open(p, "rb").read()
    pos = [0]
    def u1():
        v = data[pos[0]]; pos[0]+=1; return v
    def i2():
        v = struct.unpack_from(">h", data, pos[0])[0]; pos[0]+=2; return v
    def i4():
        v = struct.unpack_from(">i", data, pos[0])[0]; pos[0]+=4; return v
    def i8():
        v = struct.unpack_from(">q", data, pos[0])[0]; pos[0]+=8; return v
    def f4():
        v = struct.unpack_from(">f", data, pos[0])[0]; pos[0]+=4; return v
    def f8():
        v = struct.unpack_from(">d", data, pos[0])[0]; pos[0]+=8; return v
    def payload(t):
        if t==1: return u1()
        if t==2: return i2()
        if t==3: return i4()
        if t==4: return i8()
        if t==5: return f4()
        if t==6: return f8()
        if t==7:
            n=i4(); pos[0]+=n; return ("bytes",n)
        if t==8:
            n=i2(); s=data[pos[0]:pos[0]+n].decode(); pos[0]+=n; return s
        if t==9:
            et=u1(); n=i4(); return [payload(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=u1()
                if tt==0: break
                nm=payload(8); d[nm]=payload(tt)
            return d
        if t==11:
            n=i4(); pos[0]+=4*n; return ("ints",n)
        if t==12:
            n=i4(); pos[0]+=8*n; return ("longs",n)
        raise ValueError(t)
    t=u1(); payload(8)
    return payload(t)

for name, p in [
    ("new qqqq", r"d:\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.265\saves\minecraft\generated\minecraft\structures\qqqq.nbt"),
    ("chrono_anchor(old)", r"F:\MinecraftMods\endbound-ember\src\main\resources\data\endbound_ember\structures\chrono_anchor.nbt"),
    ("zombie_girl_camp", r"F:\MinecraftMods\endbound-ember\src\main\resources\data\endbound_ember\structures\zombie_girl_camp.nbt"),
]:
    try:
        r = load(p)
    except Exception as e:
        print(name, "ERR", e); continue
    pal = r.get("palette", [])
    blk = r.get("blocks", [])
    print("="*70)
    print(name, "DataVersion=", r.get("DataVersion"), "size=", r.get("size"), "palette=", len(pal), "blocks=", len(blk), "entities=", len(r.get("entities", [])))
    used = collections.Counter()
    for b in blk:
        st = b.get("state")
        nm = pal[st].get("Name") if st is not None and st < len(pal) else "?"
        if nm in ("minecraft:air",): continue
        used[nm] += 1
    for k, v in used.most_common(25):
        print(f"  {v:5d} {k}")
    nbtids = collections.Counter()
    for b in blk:
        if b.get("nbt"):
            nbtids[b["nbt"].get("id")] += 1
    print("  nbt block entities:", dict(nbtids))
