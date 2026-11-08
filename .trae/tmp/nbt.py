import gzip, struct, sys, collections, json

p = r"d:\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.265\saves\minecraft\generated\minecraft\structures\qqqq.nbt"
data = gzip.open(p, "rb").read()

pos = 0
def u1():
    global pos
    v = data[pos]; pos += 1; return v
def i2():
    global pos
    v = struct.unpack_from(">h", data, pos)[0]; pos += 2; return v
def i4():
    global pos
    v = struct.unpack_from(">i", data, pos)[0]; pos += 4; return v
def i8():
    global pos
    v = struct.unpack_from(">q", data, pos)[0]; pos += 8; return v
def f4():
    global pos
    v = struct.unpack_from(">f", data, pos)[0]; pos += 4; return v
def f8():
    global pos
    v = struct.unpack_from(">d", data, pos)[0]; pos += 8; return v

def read_payload(t):
    if t == 1: return u1()
    if t == 2: return i2()
    if t == 3: return i4()
    if t == 4: return i8()
    if t == 5: return f4()
    if t == 6: return f8()
    if t == 7:
        n = i4(); b = data[pos:pos+n]; globals()['pos'] = pos+n; return ("bytes", n)
    if t == 8:
        n = i2(); s = data[pos:pos+n].decode("utf-8"); globals()['pos'] = pos+n; return s
    if t == 9:
        et = u1(); n = i4(); return [read_payload(et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = u1()
            if tt == 0: break
            name = read_payload(8)
            d[name] = read_payload(tt)
        return d
    if t == 11:
        n = i4(); b = data[pos:pos+4*n]; globals()['pos'] = pos+4*n; return ("ints", n)
    if t == 12:
        n = i4(); b = data[pos:pos+8*n]; globals()['pos'] = pos+8*n; return ("longs", n)
    raise ValueError("bad tag %d at %d" % (t, pos))

root_type = u1()
root_name = read_payload(8)
root = read_payload(root_type)

print("root:", root_name, "bytes:", len(data))
print("DataVersion:", root.get("DataVersion"))
print("size:", root.get("size"))
palette = root.get("palette", [])
blocks = root.get("blocks", [])
print("palette entries:", len(palette), "blocks:", len(blocks), "entities:", len(root.get("entities", [])))

cnt = collections.Counter()
for ent in palette:
    name = ent.get("Name")
    cnt[name] += 1
print("\n=== palette (count of palette entries per block id) ===")
for k, v in sorted(cnt.items()):
    print(f"{v:3d}  {k}")

used = collections.Counter()
nbt_used = collections.Counter()
for b in blocks:
    st = b.get("state")
    name = palette[st].get("Name") if st is not None and st < len(palette) else "?"
    used[name] += 1
    if "nbt" in b and b["nbt"]:
        nbt_used[(name, b["nbt"].get("id", "-"))] += 1
print("\n=== placed blocks (by id) ===")
for k, v in used.most_common():
    print(f"{v:4d}  {k}")
print("\n=== blocks with NBT ===")
for (name, nid), v in nbt_used.most_common():
    print(f"{v:4d}  {name}  nbt-id={nid}")

print("\n=== palette entries with Properties ===")
for i, ent in enumerate(palette):
    pr = ent.get("Properties")
    print(i, ent.get("Name"), pr if pr else "")

print("\n=== non-minecraft namespaces ===")
for k, v in sorted(cnt.items()):
    if not k.startswith("minecraft:"):
        print(v, k)

ents = root.get("entities", [])
print("\n=== entities ===")
for e in ents[:20]:
    print(e.get("pos"), e.get("blockPos"), e.get("nbt", {}).get("id"))
