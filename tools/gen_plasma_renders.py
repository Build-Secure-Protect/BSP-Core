#!/usr/bin/env python3
"""Builds tools/preview/plasma_renders.html: the plasma blocks and the batteries drawn from the mod's own model
JSON files and textures with three.js, as close to the in-game look as a page can get (the parts the game draws
with renderers, such as plasma in tanks, the interface's lit crosses, the repeater's rings and the valve's wheel,
are added by hand in the page). Each view is one scene; ?view=slug shows one scene at 800 x 500, which is how
the pictures for the CurseForge reference sheet are captured (docs/curseforge/images/render_*.jpg).

  python3 tools/gen_plasma_renders.py
"""
import base64
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bsp_core"
BLOCKS = ["plasma_extractor", "plasma_interface", "plasma_valve", "plasma_repeater", "projector_base", "totem_projector", "battery_charger",
          "plasma_battery_1", "plasma_battery_2", "plasma_battery_3", "plasma_battery_4", "tetrium_core_cable", "charged_illyrium_core_cable", "magnatite_core_cable", "illyrium_core_cable"]
DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
BLOCKS += [f"{d}_illyrium_core_cable" for d in DYES] + ["blue_magnatite_core_cable", "red_magnatite_core_cable", "blue_charged_illyrium_core_cable", "red_charged_illyrium_core_cable"]
BLOCKS += ["tank_casing", "tank_glass", "tank_port"]
BLOCKS += ["tetrium_crucible", "combination_forge", "factory_motivator", "admin_rack", "anti_totem", "decoy_totem", "decoy_power_base", "plasma_injector"]
# every block the sheet cannot show from a flat texture, drawn as an inventory-style icon by ?icons=1: id, kind, blockstate properties
ICONS = [["shatter_totem", "item", {}], ["wave_emitter", "item", {}], ["tetrium_crucible", "block", {}], ["combination_forge", "block", {}],
         ["factory_motivator", "block", {}], ["admin_rack", "block", {"facing": "north"}], ["anti_totem", "block", {}], ["decoy_totem", "block", {"facing": "north"}],
         ["decoy_power_base", "block", {}], ["totem_projector", "block", {}], ["plasma_extractor", "block", {}],
         ["plasma_interface", "block", {d: "none" for d in ("north", "south", "east", "west", "up", "down")}],
         ["plasma_repeater", "block", {"facing": "west"}], ["plasma_valve", "block", {"axis": "x"}], ["projector_base", "block", {}],
         ["battery_charger", "block", {"facing": "north"}], ["plasma_injector", "block", {"facing": "north"}], ["tank_port", "block", {}]]
ICONS += [[f"plasma_battery_{i}", "block", {"axis": "x"}] for i in range(1, 5)]
ICONS += [[c, "block", {"east": True, "west": True}] for c in ("tetrium_core_cable", "magnatite_core_cable", "illyrium_core_cable", "charged_illyrium_core_cable")]
CUBE_ALL = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}}]
ITEMS = ["wave_emitter", "power_cell_1", "power_cell_2", "power_cell_3", "wrench"]
MODELS, TEXTURES = {}, {}


def load_model(ref):
    """A model by 'bsp_core:block/x' or 'bsp_core:item/x', with its parent chain folded in (minecraft parents are ignored)."""
    if ref in MODELS:
        return MODELS[ref]
    ns, path = ref.split(":")
    if ns != "bsp_core":
        return None
    m = json.loads((ASSETS / f"models/{path}.json").read_text())
    parent = load_model(m["parent"]) if m.get("parent", "").startswith("bsp_core:") else None
    elements = m.get("elements") or (parent["elements"] if parent else [])
    if not elements and m.get("parent") == "minecraft:block/cube_all":
        elements = CUBE_ALL
    out = {"elements": elements, "textures": dict(parent["textures"] if parent else {}, **m.get("textures", {})),
           "sprite": m.get("parent") == "minecraft:item/generated"}
    for key, tex in out["textures"].items():
        if not tex.startswith("#"):
            load_texture(tex)
    MODELS[ref] = out
    return out


FRAME = {"bsp_core:block/shatter_totem_stealing": 4}  # animated textures shown at this frame (the steal flashes gold and red: show the red)


def load_texture(ref):
    if ref in TEXTURES:
        return
    ns, path = ref.split(":")
    data = (ASSETS / f"textures/{path}.png").read_bytes()
    w, h = struct.unpack(">II", data[16:24])
    if ref in FRAME:
        from gen_material_assets import read_png, write_png
        import tempfile, os
        _, _, rows = read_png(data)
        y0 = FRAME[ref] * w
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
            tmp = Path(f.name)
        write_png(tmp, w, w, lambda x, y: rows[y0 + y][x])
        data, h = tmp.read_bytes(), w
        os.unlink(tmp)
    TEXTURES[ref] = {"src": "data:image/png;base64," + base64.b64encode(data).decode(), "w": w, "h": h}


def main():
    states = {}
    for b in BLOCKS:
        states[b] = json.loads((ASSETS / f"blockstates/{b}.json").read_text())
        for part in states[b].get("multipart", []):
            load_model(part["apply"]["model"])
        for v in states[b].get("variants", {}).values():
            load_model(v["model"])
    for state in ("owned", "unclaimed", "stealing"):
        states["shatter_totem" if state == "owned" else f"shatter_totem_{state}"] = {"variants": {"": {"model": f"bsp_core:block/shatter_totem_{state}"}}}
        load_model(f"bsp_core:block/shatter_totem_{state}")
    load_model("bsp_core:item/shatter_totem")
    for i in ITEMS:
        load_model(f"bsp_core:item/{i}")
    page = TEMPLATE.replace("__STATES__", json.dumps(states)).replace("__MODELS__", json.dumps(MODELS)).replace("__TEXTURES__", json.dumps(TEXTURES)).replace("__ICONS__", json.dumps(ICONS))
    assert all(ord(c) < 128 for c in page)
    (ROOT / "tools/preview/plasma_renders.html").write_text(page)
    print("renders page written:", len(MODELS), "models,", len(TEXTURES), "textures,", len(page) // 1024, "KB")


TEMPLATE = r'''<title>Plasma Renders</title>
<style>
:root{ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --tq:#19d3b0; color-scheme:dark; }
body{ background:var(--bg); color:var(--fg); margin:0; font:14px/1.5 "IBM Plex Sans",system-ui,sans-serif; }
.wrap{ max-width:980px; margin:0 auto; padding-block:24px 48px; padding-inline:16px; display:flex; flex-direction:column; gap:18px; }
h1{ font-size:22px; margin:0; } p{ margin:0; color:var(--muted); }
.card{ background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:12px; display:flex; flex-direction:column; gap:8px; }
.view{ width:800px; max-width:100%; aspect-ratio:8/5; background:radial-gradient(ellipse at 50% 70%,#262b36 0%,#0c0e12 70%); cursor:grab; touch-action:none; overflow:hidden; }
body.one{ overflow:hidden; } body.one .wrap{ padding:0; max-width:none; } body.one h1, body.one p, body.one .card{ display:none; } body.one .card.show{ display:block; padding:0; border:0; background:none; }
body.one .view{ width:800px; height:500px; aspect-ratio:auto; }
body.icons{ background:transparent; } body.icons .wrap{ padding:0; max-width:none; gap:0; } body.icons h1, body.icons p{ display:none; }
.icongrid{ display:grid; grid-template-columns:repeat(8,96px); width:768px; } .icell{ width:96px; height:96px; }
</style>
<div class="wrap"><h1>Plasma Renders</h1><p>The plasma blocks drawn from the mod's own models and textures. Drag to turn. Add ?view=slug to show one scene at 800 x 500.</p><div id="cards"></div></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
(function(){
  const STATES=__STATES__, MODELS=__MODELS__, TEXTURES=__TEXTURES__, ICONS=__ICONS__;
  const ION=0x4cb2fa, COPPER=0xc87a3c, HULL2=0x1e222b;
  const DYES=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black'];
  const texCache={};
  function texture(ref){ if(texCache[ref]) return texCache[ref]; const t=new THREE.TextureLoader().load(TEXTURES[ref].src); t.magFilter=THREE.NearestFilter; t.minFilter=THREE.NearestFilter; t.flipY=true; texCache[ref]=t; return t; }
  const matCache={};
  function material(ref,shade){ const key=ref+(shade?'s':'b'); if(matCache[key]) return matCache[key]; const t=texture(ref); const m=shade?new THREE.MeshLambertMaterial({map:t,transparent:true,alphaTest:.02}):new THREE.MeshBasicMaterial({map:t,transparent:true,alphaTest:.02}); matCache[key]=m; return m; }
  function resolve(model,ref){ let r=ref; for(let i=0;i<6&&r&&r[0]==='#';i++) r=model.textures[r.slice(1)]; return r; }
  const ORDER=['east','west','up','down','south','north'];
  // one model's elements as meshes, in 1/16 units, into group g
  function elements(g,model){ for(const e of model.elements){ const s=[e.to[0]-e.from[0],e.to[1]-e.from[1],e.to[2]-e.from[2]]; if(s[0]<=0||s[1]<=0||s[2]<=0) continue; const geo=new THREE.BoxGeometry(s[0],s[1],s[2]); const uv=geo.attributes.uv; const mats=[];
      ORDER.forEach((face,i)=>{ const f=e.faces[face]; if(!f){ mats.push(new THREE.MeshBasicMaterial({visible:false})); return; } const ref=resolve(model,f.texture); if(!ref||!TEXTURES[ref]){ mats.push(new THREE.MeshBasicMaterial({color:0xff00ff})); return; }
        const T=TEXTURES[ref], frac=T.w/T.h; const [u0,v0,u1,v1]=(f.uv||[0,0,16,16]).map(v=>v/16); const V=v=>1-v*frac; uv.setXY(i*4,u0,V(v0)); uv.setXY(i*4+1,u1,V(v0)); uv.setXY(i*4+2,u0,V(v1)); uv.setXY(i*4+3,u1,V(v1)); mats.push(material(ref,e.shade!==false)); });
      const m=new THREE.Mesh(geo,mats), c=[(e.from[0]+e.to[0])/2,(e.from[1]+e.to[1])/2,(e.from[2]+e.to[2])/2];
      if(e.rotation){ const p=new THREE.Group(), o=e.rotation.origin; p.position.set(o[0],o[1],o[2]); p.rotation[e.rotation.axis]=e.rotation.angle*Math.PI/180; m.position.set(c[0]-o[0],c[1]-o[1],c[2]-o[2]); p.add(m); g.add(p); } else { m.position.set(c[0],c[1],c[2]); g.add(m); } } }
  function placeModel(g,ref,rx,ry){ const model=MODELS[ref]; if(!model) return; const part=new THREE.Group(); elements(part,model); const turn=new THREE.Group(); part.position.set(-8,-8,-8); turn.add(part); turn.rotation.order='YXZ'; turn.rotation.y=-(ry||0)*Math.PI/180; turn.rotation.x=-(rx||0)*Math.PI/180; turn.position.set(8,8,8); g.add(turn); }
  function matches(when,props){ for(const k in when){ if(k==='OR') { if(!when.OR.some(w=>matches(w,props))) return false; continue; } const want=String(when[k]).split('|'); if(!want.includes(String(props[k]))) return false; } return true; }
  // a block at (x,y,z) in block units with blockstate properties, drawn from its blockstate file
  function block(g,id,x,y,z,props){ const st=STATES[id]; const grp=new THREE.Group(); grp.position.set(x*16,y*16,z*16); g.add(grp); props=props||{};
    if(st.multipart){ for(const p of st.multipart){ if(p.when&&!matches(p.when,props)) continue; placeModel(grp,p.apply.model,p.apply.x,p.apply.y); } }
    else { let key=Object.entries(props).map(([k,v])=>k+'='+v).join(','); let v=st.variants[key]; if(!v){ for(const k in st.variants){ const parts=k?k.split(','):[]; if(parts.every(pv=>{ const [pk,pvv]=pv.split('='); return String(props[pk])===pvv; })){ v=st.variants[k]; break; } } } if(!v) v=Object.values(st.variants)[0]; placeModel(grp,v.model,v.x,v.y); }
    return grp; }
  function box(g,f,t,c,op,glow){ const mat=glow?new THREE.MeshBasicMaterial({color:c}):new THREE.MeshLambertMaterial({color:c}); if(op!==undefined){ mat.transparent=true; mat.opacity=op; } const m=new THREE.Mesh(new THREE.BoxGeometry(t[0]-f[0],t[1]-f[1],t[2]-f[2]),mat); m.position.set((f[0]+t[0])/2,(f[1]+t[1])/2,(f[2]+t[2])/2); g.add(m); return m; }
  function ring(g,p,axis,R,r,c){ const m=new THREE.Mesh(new THREE.TorusGeometry(R,r,8,24),new THREE.MeshLambertMaterial({color:c||COPPER})); m.position.set(...p); if(axis==='y') m.rotation.x=Math.PI/2; if(axis==='x') m.rotation.y=Math.PI/2; g.add(m); return m; }
  const plasmaMat=()=>new THREE.MeshBasicMaterial({color:ION,transparent:true,opacity:.95});
  function plasma(g,f,t){ const m=new THREE.Mesh(new THREE.BoxGeometry(t[0]-f[0],t[1]-f[1],t[2]-f[2]),plasmaMat()); m.position.set((f[0]+t[0])/2,(f[1]+t[1])/2,(f[2]+t[2])/2); g.add(m); return m; }
  // ---- scene helpers: a map of what stands where, so cables and interfaces connect the way the game connects them
  const DIRS={down:[0,-1,0],up:[0,1,0],north:[0,0,-1],south:[0,0,1],west:[-1,0,0],east:[1,0,0]};
  const AXIS={x:['east','west'],y:['up','down'],z:['north','south']};
  function World(){ this.map=new Map(); }
  World.prototype.put=function(id,x,y,z,props){ this.map.set([x,y,z].join(','),{id,x,y,z,props:props||{}}); };
  World.prototype.at=function(x,y,z){ return this.map.get([x,y,z].join(',')); };
  const opp={down:'up',up:'down',north:'south',south:'north',west:'east',east:'west'};
  const colourOf=id=>{ const m=id.match(/^([a-z_]+?)_(magnatite|illyrium|charged_illyrium|tetrium)_core_cable$/); return m?m[1]:(id.endsWith('_core_cable')?'plain':null); };
  function endOf(b,dir,from){ // does block b accept a cable from direction dir (dir = from b toward the neighbour)?
    if(!b) return false; const id=b.id;
    if(from&&from.id.endsWith('_core_cable')&&id.endsWith('_core_cable')){ const link=(from.props.ends||{})[opp[dir]]==='link'||(b.props.ends||{})[dir]==='link'; if(!link&&colourOf(from.id)!==colourOf(id)) return false; }
    if(from&&from.props.ends&&from.props.ends[opp[dir]]==='off') return false; if(b.props.ends&&b.props.ends[dir]==='off') return false;
    if(id.endsWith('_core_cable')||id==='projector_base'||id==='plasma_interface') return true;
    if(id==='plasma_valve') return AXIS[b.props.axis||'x'].includes(dir);
    if(id==='plasma_repeater') return AXIS[(b.props.facing||'east')==='east'||(b.props.facing)==='west'?'x':(b.props.facing==='up'||b.props.facing==='down')?'y':'z'].includes(dir);
    if(id==='battery_charger') return dir===opp[b.props.facing||'north'];
    if(id==='plasma_injector') return dir===opp[b.props.facing||'north'];
    return false; }
  function build(g,w){ for(const b of w.map.values()){ const props=Object.assign({},b.props);
      if(b.id.endsWith('_core_cable')){ for(const d in DIRS){ const v=DIRS[d]; const n=w.at(b.x+v[0],b.y+v[1],b.z+v[2]); props[d]=endOf(n,opp[d],b); } }
      if(b.id==='plasma_interface'){ for(const d in DIRS){ const v=DIRS[d]; const n=w.at(b.x+v[0],b.y+v[1],b.z+v[2]); props[d]=!n?'none':n.id==='plasma_extractor'?'drum':n.id==='plasma_interface'?'join':endOf(n,opp[d])?'cable':'none'; } }
      const grp=block(g,b.id,b.x,b.y,b.z,props); extras(grp,b,props,w); } }
  // ---- the parts the game draws with renderers
  function extras(g,b,props,w){ const id=b.id;
    if(id==='plasma_injector'){ const ax=new THREE.Group(); ax.position.set(8,8,8); ax.rotation.y={north:0,east:-Math.PI/2,south:Math.PI,west:Math.PI/2}[props.facing||'north']; g.add(ax); const o=[-8,-8,-8];
      const P=(f,t)=>plasma(ax,[f[0]+o[0],f[1]+o[1],f[2]+o[2]],[t[0]+o[0],t[1]+o[1],t[2]+o[2]]); P([4.6,4.6,6.3],[11.4,11.4,11.5]); for(const [x,y] of [[4,4],[10,4],[4,10],[10,10]]) P([x+.4,y+.4,.6],[x+1.6,y+1.6,2.4]);
      box(ax,[7.2+o[0],12.4+o[1],12.7+o[2]],[8.8+o[0],13.2+o[1],13.3+o[2]],0x19d3b0,undefined,true); }
    if(id==='projector_base'){ plasma(g,[2.2,4.55,2.2],[13.8,10.9,13.8]); for(const n of [[6.3,6.3,-0.5,9.7,9.7,0.2],[6.3,6.3,15.8,9.7,9.7,16.5],[-0.5,6.3,6.3,0.2,9.7,9.7],[15.8,6.3,6.3,16.5,9.7,9.7]]) plasma(g,[n[0],n[1],n[2]],[n[3],n[4],n[5]]); }
    if(id.endsWith('_core_cable')){ const cx=props.east||props.west, cz=props.north||props.south, cy=props.up||props.down; plasma(g,[cx?0.3:6.4,cy?0.3:6.4,cz?0.3:6.4],[cx?15.7:9.6,cy?15.7:9.6,cz?15.7:9.6]); }
    if(id.endsWith('_core_cable')&&b.props.ends){ for(const d in b.props.ends){ const mode=b.props.ends[d]; const ax=new THREE.Group(); ax.position.set(8,8,8); ax.rotation.y={east:0,west:Math.PI,south:-Math.PI/2,north:Math.PI/2}[d]||0; g.add(ax); const o=[-8,-8,-8];
        const B=(f,t,c,gl)=>box(ax,[f[0]+o[0],f[1]+o[1],f[2]+o[2]],[t[0]+o[0],t[1]+o[1],t[2]+o[2]],c,undefined,gl);
        if(mode==='off'){ B([10.6,4.6,4.6],[11.4,11.4,11.4],HULL2); B([11.4,6.2,6.2],[11.9,9.8,9.8],0x8a909c); continue; }
        const c=mode==='link'?0xf4f7fa:COPPER; B([13.2,10.6,4.7],[15.2,11.3,11.3],c); B([13.2,4.7,4.7],[15.2,5.4,11.3],c); B([13.2,5.4,4.7],[15.2,10.6,5.4],c); B([13.2,5.4,10.6],[15.2,10.6,11.3],c);
        if(mode==='link'){ B([13.2,10.6,4.7],[14.1,11.3,11.3],COPPER); B([13.2,4.7,4.7],[14.1,5.4,11.3],COPPER); B([13.2,5.4,4.7],[14.1,10.6,5.4],COPPER); B([13.2,5.4,10.6],[14.1,10.6,11.3],COPPER); }
        else { const out=mode==='out'; B([11.4,11.3,7.4],[15.6,11.75,8.6],0x19d3b0,true); B([out?15.2:11.4,11.3,6.3],[out?16.1:12.3,11.75,9.7],0x19d3b0,true); B([out?16.1:10.9,11.3,7.2],[out?16.6:11.4,11.75,8.8],0x19d3b0,true); } } }
    if(id==='plasma_repeater'){ const ax=new THREE.Group(); ax.position.set(8,8,8); const f=props.facing||'east'; ax.rotation.y={east:0,west:Math.PI,south:-Math.PI/2,north:Math.PI/2}[f]||0; g.add(ax); for(let i=0;i<5;i++){ const x=2.6+i*2.2+0.7-8; ring(ax,[x,0,0],'x',4.8,.65); } }
    if(id==='plasma_valve'){ const ax=new THREE.Group(); ax.position.set(8,8,8); ax.rotation.y=(props.axis==='z')?-Math.PI/2:0; g.add(ax); ring(ax,[0,7.3,0],'y',3.4,.5); box(ax,[-3.1,7.05,-0.3],[3.1,7.55,0.3],COPPER); box(ax,[-0.3,7.05,-3.1],[0.3,7.55,3.1],COPPER);
      box(ax,[-0.25,-0.4,4.1],[0.25,1.9,4.35],0xffd23a,undefined,true); box(ax,[-4.9,1.4,3.9],[-3.7,2.4,4.25],ION,undefined,true); plasma(ax,[-6.4,-2.6,-2.6],[-2.6,2.6,2.6]); plasma(ax,[2.6,-2.6,-2.6],[6.4,2.6,2.6]); }
    if(id==='battery_charger'){ const ax=new THREE.Group(); ax.position.set(8,8,8); ax.rotation.y={north:0,east:-Math.PI/2,south:Math.PI,west:Math.PI/2}[props.facing||'north']; g.add(ax); plasma(ax,[-4,-5,-4],[4,-4.4,4]); plasma(ax,[-4,3.4,-4],[4,4,4]); plasma(ax,[-1.7,-1.7,7.8],[1.7,1.7,8.5]);
      if(b.props.item){ const inner=new THREE.Group(); inner.position.set(0,-0.5,0); inner.scale.setScalar(.45); const bat=new THREE.Group(); bat.position.set(-8,-8,-8); placeModel(bat,'bsp_core:block/'+b.props.item,0,0); inner.add(bat); ax.add(inner); } }
    if(id==='plasma_interface'){ const refused=!!b.props.refused, lit=b.props.lit!==false; const c=refused?0xff4a3a:lit?0x4fb8ff:0x5a6270; const has=(dx,dy,dz)=>{ const n=w.at(b.x+dx,b.y+dy,b.z+dz); return !!n&&n.id==='plasma_interface'; };
      const E=Object.entries(DIRS); for(let i=0;i<6;i++) for(let j=i+1;j<6;j++){ const [d1,v1]=E[i],[d2,v2]=E[j]; if(v1.some((a,k)=>a&&v2[k])) continue; if(Math.abs(v1[0])===Math.abs(v2[0])&&Math.abs(v1[1])===Math.abs(v2[1])&&Math.abs(v1[2])===Math.abs(v2[2])) continue; const n1=has(...v1),n2=has(...v2),dg=has(v1[0]+v2[0],v1[1]+v2[1],v1[2]+v2[2]);
        if((!n1&&!n2)||(n1&&n2&&!dg)){ const lo=[-0.3,-0.3,-0.3],hi=[16.3,16.3,16.3]; for(const v of [v1,v2]){ for(let a=0;a<3;a++){ if(v[a]>0) lo[a]=16-1.6; if(v[a]<0) hi[a]=1.6; } } box(g,lo,hi,HULL2); } }
      for(const d in DIRS){ const v=DIRS[d]; if(has(...v)) continue; const clear=props[d]==='none'; const a=v[0]?0:v[1]?1:2; const inn=a===0?[1,2]:a===1?[0,2]:[0,1]; const face=(u0,v0,u1,v1,p0,p1,k)=>{ const lo=[0,0,0],hi=[0,0,0]; if(v[a]>0){ lo[a]=16+p0; hi[a]=16+p1; } else { lo[a]=-p1; hi[a]=-p0; } lo[inn[0]]=u0; hi[inn[0]]=u1; lo[inn[1]]=v0; hi[inn[1]]=v1; box(g,lo,hi,c,undefined,true); };
        if(clear){ face(2.8,7.2,13.2,8.8,.15,.7); face(7.2,2.8,8.8,13.2,.15,.7); face(6.3,6.3,9.7,9.7,.15,1); } else { face(2.8,7.2,5.3,8.8,.15,.7); face(10.7,7.2,13.2,8.8,.15,.7); face(7.2,2.8,8.8,5.3,.15,.7); face(7.2,10.7,8.8,13.2,.15,.7); } } }
    if(id==='totem_projector'&&b.props.aura){ const r=b.props.aura; const e=new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(16*(2*r+1),16*(2*r+1),16*(2*r+1))),new THREE.LineBasicMaterial({color:0x19d3b0,transparent:true,opacity:.6})); e.position.set(8,8,8); g.add(e); } }
  function ground(g,x0,z0,x1,z1,y){ box(g,[x0*16,y*16-6,z0*16],[x1*16,y*16,z1*16],0x3f5a30); }
  // ---- the scenes
  function network(g){ const w=new World(); w.put('plasma_extractor',0,0,0); w.put('shatter_totem',0,1,0); w.put('plasma_interface',1,0,0); w.put('plasma_interface',2,0,0); w.put('plasma_interface',1,0,1,{}); w.put('plasma_interface',2,0,1);
    for(const x of [3,4,5,7,9]) w.put('tetrium_core_cable',x,0,0); w.put('plasma_valve',6,0,0,{axis:'x'}); w.put('plasma_repeater',8,0,0,{facing:'east'}); w.put('projector_base',10,0,0); w.put('totem_projector',10,1,0);
    for(const z of [2,3]) w.put('charged_illyrium_core_cable',1,0,z); w.put('battery_charger',1,0,4,{facing:'south',item:'plasma_battery_2'});
    for(const z of [2,3,4]) w.put('tetrium_core_cable',2,0,z); w.put('tetrium_core_cable',3,0,4); w.put('projector_base',4,0,4); w.put('totem_projector',4,1,4);
    build(g,w); ground(g,-2,-2,13,7,0); return {target:[88,8,30],dist:230,ry:Math.PI*1.22,rx:.55}; }
  function group(g){ const w=new World(); w.put('plasma_extractor',0,0,0); w.put('shatter_totem',0,1,0); for(const [x,z] of [[1,0],[2,0],[1,1],[2,1],[1,2],[2,2]]) w.put('plasma_interface',x,0,z); w.put('plasma_interface',1,1,0); w.put('plasma_interface',2,1,0);
    w.put('tetrium_core_cable',3,0,1); w.put('tetrium_core_cable',4,0,1); w.put('plasma_interface',4,0,3,{refused:true}); w.put('tetrium_core_cable',3,1,0); build(g,w); ground(g,-2,-2,7,6,0); return {target:[32,12,16],dist:120,ry:Math.PI*1.2,rx:.5}; }
  function valve(g){ const w=new World(); w.put('tetrium_core_cable',0,0,0); w.put('plasma_valve',1,0,0,{axis:'x'}); w.put('tetrium_core_cable',2,0,0); build(g,w); return {target:[24,10,8],dist:70,ry:Math.PI*1.25,rx:.45}; }
  function repeater(g){ const w=new World(); w.put('illyrium_core_cable'in STATES?'tetrium_core_cable':'tetrium_core_cable',0,0,0); w.put('plasma_repeater',1,0,0,{facing:'east'}); w.put('tetrium_core_cable',2,0,0); build(g,w); return {target:[24,8,8],dist:66,ry:Math.PI*1.25,rx:.45}; }
  function base(g){ const w=new World(); w.put('tetrium_core_cable',0,0,0); w.put('tetrium_core_cable',1,0,0); w.put('projector_base',2,0,0); w.put('totem_projector',2,1,0,{aura:0}); build(g,w); ground(g,-1,-1,4,2,0); return {target:[30,14,8],dist:90,ry:Math.PI*1.2,rx:.4}; }
  function charger(g){ const w=new World(); w.put('tetrium_core_cable',0,0,0); w.put('tetrium_core_cable',0,0,1); w.put('battery_charger',0,0,2,{facing:'south',item:'plasma_battery_3'}); build(g,w); ground(g,-1,-1,2,4,0); return {target:[8,8,24],dist:78,ry:Math.PI*0.25,rx:.42}; }
  function batteries(g){ const w=new World(); for(let i=0;i<4;i++) w.put('plasma_battery_'+(i+1),i*2,0,0,{axis:'x'}); w.put('plasma_extractor',1,0,3); w.put('plasma_battery_2',1,1,3,{axis:'x'}); w.put('plasma_interface',2,0,3); w.put('tetrium_core_cable',3,0,3); w.put('tetrium_core_cable',4,0,3); build(g,w); ground(g,-1,-1,8,5,0); return {target:[52,8,28],dist:140,ry:Math.PI*1.2,rx:.5}; }
  function emitter(g){ const e=new THREE.Group(); placeModel(e,'bsp_core:item/wave_emitter',0,0); e.scale.setScalar(1.3); e.position.set(0,0,0); g.add(e);
    [1,2,3].forEach((t,i)=>{ const T=TEXTURES['bsp_core:item/power_cell_'+t]; const m=new THREE.Mesh(new THREE.PlaneGeometry(14,14),new THREE.MeshBasicMaterial({map:texture('bsp_core:item/power_cell_'+t),transparent:true,alphaTest:.1,side:THREE.DoubleSide})); m.position.set(30+i*16,8,8); g.add(m); });
    const wT=texture('bsp_core:item/wrench'); const wr=new THREE.Mesh(new THREE.PlaneGeometry(14,14),new THREE.MeshBasicMaterial({map:wT,transparent:true,alphaTest:.1,side:THREE.DoubleSide})); wr.position.set(30+3*16,8,8); g.add(wr);
    return {target:[42,8,8],dist:118,ry:Math.PI,rx:.12,still:true}; }
  function colours(g){ const w=new World(); DYES.forEach((d,i)=>{ for(let x=0;x<3;x++) w.put(d+'_illyrium_core_cable',x,0,i); });
    w.put('illyrium_core_cable',0,0,17); w.put('illyrium_core_cable',1,0,17); w.put('illyrium_core_cable',2,0,17); build(g,w); ground(g,-1,-1,4,19,0); return {target:[24,2,136],dist:260,ry:Math.PI*0.72,rx:.5}; }
  function ends(g){ const w=new World(); w.put('blue_illyrium_core_cable',0,0,0,{ends:{west:'out',north:'in',south:'off',east:'link'}}); w.put('blue_illyrium_core_cable',-1,0,0); w.put('blue_illyrium_core_cable',0,0,-1); w.put('blue_illyrium_core_cable',0,0,1); w.put('red_illyrium_core_cable',1,0,0); w.put('red_illyrium_core_cable',2,0,0);
    w.put('blue_magnatite_core_cable',0,0,3); w.put('blue_magnatite_core_cable',1,0,3); w.put('red_magnatite_core_cable',2,0,3); w.put('red_magnatite_core_cable',3,0,3); // a blue run meeting a red one without a link: no join
    build(g,w); ground(g,-2,-2,5,5,0); return {target:[12,4,20],dist:100,ry:Math.PI*1.22,rx:.55}; }
  function tank(g){ const W=5,H=4,D=5; const ports={'2,3,2':1,'0,1,2':1}; const face=new Set(['2,1,0','1,3,1']);
    for(let x=0;x<W;x++) for(let y=0;y<H;y++) for(let z=0;z<D;z++){ const ex=x===0||x===W-1, ey=y===0||y===H-1, ez=z===0||z===D-1; const edges=(ex?1:0)+(ey?1:0)+(ez?1:0); if(!edges) continue; const key=x+','+y+','+z;
      if(ports[key]){ block(g,'tank_port',x,y,z,{formed:true}); continue; }
      if(edges>=2||face.has(key)){ const p={formed:true}; const ax=[[ex,x,W],[ey,y,H],[ez,z,D]]; 'xyz'.split('').forEach((a,i)=>{ p['along_'+a]=edges>=2&&(edges===3||!ax[i][0]); p['hi_'+a]=ax[i][1]===ax[i][2]-1; }); block(g,'tank_casing',x,y,z,p); }
      else block(g,'tank_glass',x,y,z,{formed:true}); }
    const frac=.6; const top=8+(H-1)*16*frac; plasma(g,[8,8,8],[(W-1)*16+8,top,(D-1)*16+8]);
    for(const [cx,cz] of [[-0.3,-0.3],[W*16-0.9,-0.3],[-0.3,D*16-0.9],[W*16-0.9,D*16-0.9]]) box(g,[cx,4,cz],[cx+1.2,Math.min(top,H*16-4),cz+1.2],0x19d3b0,undefined,true);
    ground(g,-1,-1,W+1,D+1,0); return {target:[40,30,40],dist:200,ry:Math.PI*1.2,rx:.4}; }
  function totem(g){ const w=new World(); w.put('shatter_totem',0,0,0); build(g,w); ground(g,-2,-2,3,3,0); return {target:[8,11,6],dist:64,ry:Math.PI*1.12,rx:.28}; }
  function totemStates(g){ const w=new World(); w.put('shatter_totem_stealing',0,0,0); w.put('shatter_totem',2,0,0); w.put('shatter_totem_unclaimed',4,0,0); build(g,w); ground(g,-1,-2,6,3,0); return {target:[40,11,6],dist:130,ry:Math.PI*1.06,rx:.25}; }
  function injector(g){ const w=new World(); w.put('tetrium_crucible',0,0,0); w.put('plasma_injector',0,0,1,{facing:'north'}); w.put('tetrium_core_cable',0,0,2); w.put('tetrium_core_cable',0,0,3); build(g,w); ground(g,-2,-1,3,5,0); return {target:[8,10,22],dist:84,ry:Math.PI*.52,rx:.38}; }
  const SCENES=[['shatter_totem','The Shatter Totem',totem],['totem_states','Unclaimed, owned and being stolen',totemStates],['plasma_injector','A Plasma Injector feeding a Tetrium Crucible',injector],['plasma_tank','A formed 5 x 4 x 5 Plasma Tank, 60 % full',tank],['coloured_cables','Sixteen colours of Illyrium cable, plain at the far end',colours],['cable_ends','Wrench-set ends: Output, Input, Off and a Link to red; below, blue meets red without a link',ends],['plasma_network','The Wave Plasma network',network],['plasma_interface_group','Six interfaces joined, one refused',group],['plasma_valve','Plasma Valve',valve],['plasma_repeater','Plasma Repeater',repeater],
    ['projector_base','Projector Base and Projector',base],['battery_charger','Battery Charger',charger],['plasma_batteries','Plasma Batteries, and one feeding an extractor',batteries],['wave_emitter','Wave Emitter, Power Cells and the Wrench',emitter]];
  const one=new URLSearchParams(location.search).get('view'); if(one) document.body.classList.add('one');
  const iconsMode=!!new URLSearchParams(location.search).get('icons'); if(iconsMode) document.body.classList.add('icons');
  const canvas=document.createElement('canvas'); canvas.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:1'; document.body.appendChild(canvas);
  const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:true,preserveDrawingBuffer:true}); renderer.setPixelRatio(1); renderer.setScissorTest(true); if(iconsMode) renderer.setClearColor(0x000000,0); else renderer.setClearColor(0x12151b,1);
  const views=[];
  function lights(scene){ scene.add(new THREE.AmbientLight(0xffffff,.8)); const d=new THREE.DirectionalLight(0xffffff,.75); d.position.set(60,120,-80); scene.add(d); const d2=new THREE.DirectionalLight(0xffffff,.25); d2.position.set(-60,40,80); scene.add(d2); }
  if(iconsMode){ const grid=document.getElementById('cards'); grid.className='icongrid';
    for(const [id,kind,props] of ICONS){ const cell=document.createElement('div'); cell.className='icell'; cell.dataset.id=id; grid.appendChild(cell); const scene=new THREE.Scene(); lights(scene); const g=new THREE.Group(); scene.add(g);
      if(kind==='item') placeModel(g,'bsp_core:item/'+id,0,0); else { const w=new World(); w.put(id,0,0,0,props); const b=w.at(0,0,0); const p=Object.assign({},props); const grp=block(g,id,0,0,0,p); extras(grp,b,p,w); }
      const bb=new THREE.Box3().setFromObject(g), c=bb.getCenter(new THREE.Vector3()), sz=bb.getSize(new THREE.Vector3()); const pw=(sz.x+sz.z)*.707, ph=sz.y*.866+(sz.x+sz.z)*.354; const R=Math.max(pw,ph)/2*1.06+.5;
      const cam=new THREE.OrthographicCamera(-R,R,R,-R,-400,400); const ry=Math.PI*1.25, rx=Math.PI/6; cam.position.set(c.x+Math.sin(ry)*100*Math.cos(rx),c.y+Math.sin(rx)*100,c.z+Math.cos(ry)*100*Math.cos(rx)); cam.lookAt(c); views.push({view:cell,scene,cam,fixed:true}); } }
  for(const [slug,name,builder] of SCENES){ if(one&&one!==slug||iconsMode) continue; const card=document.createElement('div'); card.className='card'+(one?' show':''); card.innerHTML='<b>'+name+'</b> <span style="color:#9aa3b5">'+slug+'</span>'; const view=document.createElement('div'); view.className='view'; card.appendChild(view); document.getElementById('cards').appendChild(card);
    const scene=new THREE.Scene(), cam=new THREE.PerspectiveCamera(28,1.6,.1,4000); scene.add(new THREE.AmbientLight(0xffffff,.8)); const d=new THREE.DirectionalLight(0xffffff,.75); d.position.set(60,120,-80); scene.add(d); const d2=new THREE.DirectionalLight(0xffffff,.25); d2.position.set(-60,40,80); scene.add(d2);
    const g=new THREE.Group(); scene.add(g); const opts=builder(g); const v={view,scene,cam,opts,ry:opts.ry,rx:opts.rx,drag:false}; let lx=0,ly=0;
    view.addEventListener('pointerdown',e=>{v.drag=true;lx=e.clientX;ly=e.clientY;view.setPointerCapture(e.pointerId);}); view.addEventListener('pointermove',e=>{ if(!v.drag) return; v.ry+=(e.clientX-lx)*.01; v.rx=Math.max(-.2,Math.min(1.3,v.rx+(e.clientY-ly)*.01)); lx=e.clientX; ly=e.clientY; }); view.addEventListener('pointerup',()=>v.drag=false); views.push(v); }
  function frame(){ const W=innerWidth,H=innerHeight; if(canvas.width!==W||canvas.height!==H) renderer.setSize(W,H,false); renderer.setScissorTest(false); renderer.clear(); renderer.setScissorTest(true);
    for(const v of views){ const r=v.view.getBoundingClientRect(); if(r.bottom<0||r.top>H||r.width<2) continue; if(!v.fixed){ const D=v.opts.dist,T=v.opts.target; v.cam.position.set(T[0]+Math.sin(v.ry)*D*Math.cos(v.rx),T[1]+Math.sin(v.rx)*D,T[2]+Math.cos(v.ry)*D*Math.cos(v.rx)); v.cam.lookAt(T[0],T[1],T[2]); v.cam.aspect=r.width/r.height; v.cam.updateProjectionMatrix(); }
      const bottom=H-r.bottom; renderer.setViewport(r.left,bottom,r.width,r.height); renderer.setScissor(r.left,bottom,r.width,r.height); renderer.render(v.scene,v.cam); } requestAnimationFrame(frame); }
  requestAnimationFrame(frame);
  // capture: the one view, as a JPEG data URL (used by the sheet pipeline)
  window.capture=function(){ const v=views[0]; const r=v.view.getBoundingClientRect(); const out=document.createElement('canvas'); out.width=Math.round(r.width); out.height=Math.round(r.height); out.getContext('2d').drawImage(canvas,r.left,r.top,r.width,r.height,0,0,out.width,out.height); return out.toDataURL('image/jpeg',.9); };
})();
</script>
'''

if __name__ == "__main__":
    main()
