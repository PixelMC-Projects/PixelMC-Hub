// Skin dimensions and joint positions use Minecraft skin pixels.
const poses = {
  1:{ turn:-16, waist:5, lean:-4, arms:[[22,4,22,-44],[-127,10,8,-25]], steps:[8,-12] },
  2:{ turn:18, waist:6, lean:4, arms:[[36,6,24,-70],[-8,-14,18,4]], steps:[12,-3] },
  3:{ turn:-24, waist:-4, lean:-2, arms:[[10,43,64,-78],[-10,38,42,84]], steps:[3,-9] },
};
const plus = (a,b) => a.map((v,i)=>v+b[i]);
const minus = (a,b) => a.map((v,i)=>v-b[i]);
function rotate([x,y,z], axis, degrees) {
  const r=degrees*Math.PI/180, c=Math.cos(r), s=Math.sin(r);
  if (axis==='x') return [x,y*c-z*s,y*s+z*c];
  if (axis==='y') return [x*c+z*s,y,-x*s+z*c];
  return [x*c-y*s,x*s+y*c,z];
}
const around = (point,origin,turn) => plus(origin,turn(minus(point,origin)));
const corners = (width,depth) => [[-width/2,0,depth/2],[width/2,0,depth/2],[width/2,0,-depth/2],[-width/2,0,-depth/2]];

// A sleeve is one surface from shoulder to wrist. Each neighboring strip uses
// exactly the same four vertices on its boundary. Only the two ends have caps.
function addSkin(mesh, {uv,width,height,depth,stretch=0,stops=[0,height],ringAt,mirror=false}) {
  const [u,v]=uv, rim=corners(width+2*stretch,depth+2*stretch);
  const rings=stops.map((y,i)=>rim.map(delta=>ringAt(y,delta,i===0 ? -stretch : i===stops.length-1 ? stretch : 0)));
  const faceUV=[[u+depth,width],[u+depth+width,depth],[u+2*depth+width,width],[u,depth]];
  function quad(points,x,y,w,h,flip) {
    const a=flip?x+w:x, b=flip?x:x+w;
    mesh.push({points,uv:[[a,y],[b,y],[b,y+h],[a,y+h]]});
  }
  for(let j=0;j<stops.length-1;j++){
    for(let k=0;k<4;k++){
      const next=(k+1)%4, mapped=mirror && k%2 ? (k+2)%4 : k;
      const [x,w]=faceUV[mapped];
      quad([rings[j][k],rings[j][next],rings[j+1][next],rings[j+1][k]],x,v+depth+stops[j],w,stops[j+1]-stops[j],mirror);
    }
  }
  quad([3,2,1,0].map(i=>rings[0][i]),u+depth,v,width,depth,mirror);
  quad(rings.at(-1),u+depth+width,v,width,depth,mirror);
}

function createMesh(skin,rank,angle) {
  const mesh=[], pose=poses[rank] || poses[2];
  const torso=p=>around(p,[0,20,0],v=>rotate(rotate(v,'y',pose.waist),'z',pose.lean));
  function layers(param,base,outer,puff,legacyOuter=false) {
    addSkin(mesh,{...param,uv:base});
    if(skin.height64===64 || legacyOuter) addSkin(mesh,{...param,uv:outer,stretch:puff});
  }
  function rigid(top,width,height,depth,base,outer,puff,hat=false){
    layers({width,height,depth,ringAt:(y,r,edge)=>torso(plus([0,top+y+edge,0],r))},base,outer,puff,hat);
  }
  rigid(8,8,12,4,[16,16],[16,32],.18);
  rigid(.18,8,8,8,[0,0],[32,0],.45,true);
  for(const arm of [false,true]) for(const left of [false,true]) {
    const idx=Number(left), width=arm?skin.arm:4, mirror=left && skin.height64===32;
    const sign=left?1:-1, cx=sign*(arm ? 4+width/2-.45 : 2), top=arm?8:19.75;
    const [swing,reach,bend,fold]=arm?pose.arms[idx]:[pose.steps[idx],left?-5:5,-7,0];
    const pivot=[arm?-sign*(width/2-.45):0,arm?.85:.5,0];
    const turn=(vector,fraction)=>rotate(rotate(vector,'z',fold*fraction),'x',bend*fraction);
    const jointBand=Math.min(3.6,Math.max(1,(Math.abs(bend)+Math.abs(fold))/37));
    const ringAt=(y,r,edge)=>{
      const center=y<=6?[0,y,0]:plus([0,6,0],turn([0,y-6,0],1));
      const weight=Math.max(0,Math.min(1,(y-6+jointBand)/(2*jointBand)));
      const local=plus(center,turn([r[0],edge,r[2]],weight));
      const point=plus([cx,top,0],around(local,pivot,v=>rotate(rotate(v,'x',reach),'z',swing)));
      return arm?torso(point):point;
    };
    const base=arm?(left&&!mirror?[32,48]:[40,16]):(left&&!mirror?[16,48]:[0,16]);
    const overlay=arm?(left?[48,48]:[40,32]):(left?[0,48]:[0,32]);
    layers({width,height:12,depth:4,stops:[0,6-jointBand,6,6+jointBand,12],ringAt,mirror},base,overlay,.18);
  }
  for(const face of mesh) face.points=face.points.map(p=>around(p,[0,32,0],v=>rotate(rotate(v,'y',pose.turn+angle),'x',-5)));
  return mesh;
}

// Orthographic, depth-tested pixel sampling keeps the clothing in front of
// the correct arm even when the arms cross. Redraw only when skin or pose changes.
export function paintPlayer(canvas,skin,rank,angle=0) {
  const ctx=canvas.getContext('2d');
  const {width,height}=canvas, scale=width/36;
  const frame=ctx.createImageData(width,height), buffer=frame.data;
  const depths=new Float32Array(width*height).fill(-Infinity);
  const faces=createMesh(skin,rank,angle);
  for(const face of faces) {
    face.points=face.points.map(([x,y,z])=>[(18+x)*scale,(3.6+y)*scale,z]);
    face.z=face.points.reduce((sum,p)=>sum+p[2],0)/4;
  }
  faces.sort((a,b)=>a.z-b.z);
  for(const face of faces) {
    const [a,b,c,d]=face.points, [ta,tb,tc,td]=face.uv;
    const ab=minus(b,a),ad=minus(d,a);
    const nx=(ab[1]*ad[2]-ab[2]*ad[1])/scale, ny=(ab[2]*ad[0]-ab[0]*ad[2])/scale;
    const nz=(ab[0]*ad[1]-ab[1]*ad[0])/(scale*scale);
    const shade=Math.min(1.07,Math.max(.72,.85+(.16*nz-.14*ny-.07*nx)/(Math.hypot(nx,ny,nz)||1)));
    drawTriangle(a,b,c,ta,tb,tc,shade);
    drawTriangle(a,c,d,ta,tc,td,shade);
  }
  ctx.putImageData(frame,0,0);

  function drawTriangle(a,b,c,ta,tb,tc,shade) {
    const dx=b[0]-a[0],dy=b[1]-a[1],ex=c[0]-a[0],ey=c[1]-a[1];
    const area=dx*ey-dy*ex;
    if(area<1e-5) return;
    const x0=Math.max(0,Math.floor(Math.min(a[0],b[0],c[0]))),x1=Math.min(width,Math.ceil(Math.max(a[0],b[0],c[0])));
    const y0=Math.max(0,Math.floor(Math.min(a[1],b[1],c[1]))),y1=Math.min(height,Math.ceil(Math.max(a[1],b[1],c[1])));
    const factor=skin.width/64;
    for(let y=y0;y<y1;y++) for(let x=x0;x<x1;x++){
      const rx=x+.5-a[0],ry=y+.5-a[1];
      const s=(rx*ey-ry*ex)/area,t=(ry*dx-rx*dy)/area;
      if(s<0 || t<0 || s+t>1) continue;
      const pixel=y*width+x, z=a[2]+s*(b[2]-a[2])+t*(c[2]-a[2]);
      if(z<=depths[pixel]) continue;
      const u=Math.min(skin.width-1,Math.floor((ta[0]+s*(tb[0]-ta[0])+t*(tc[0]-ta[0]))*factor));
      const v=Math.min(skin.height-1,Math.floor((ta[1]+s*(tb[1]-ta[1])+t*(tc[1]-ta[1]))*factor));
      const i=(v*skin.width+u)*4, alpha=skin.data[i+3]/255;
      if(!alpha) continue;
      const out=pixel*4, old=buffer[out+3]/255*(1-alpha), opacity=alpha+old;
      for(let ch=0;ch<3;ch++) buffer[out+ch]=(skin.data[i+ch]*shade*alpha+buffer[out+ch]*old)/opacity;
      buffer[out+3]=opacity*255; depths[pixel]=z;
    }
  }
}
