/*
 * Simple Skin — the 3D preview, and a swap.
 *
 * A player model wearing one of five made-up skins; drag to turn it, like the
 * preview in the mod. Click or tap and the next skin wipes down over it. The
 * ring that then runs across the floor is the swap reaching the others — the
 * two players at the back look up when it gets to them.
 */
export default function scene(stage) {
  const { THREE, frame } = stage;
  stage.lights({ ambient: 0.8, key: 2, rim: 20 });
  stage.renderer.localClippingEnabled = true;

  /* -------------------------------------------------------------- skins */

  // Invented for this page. One pixel of a skin is a tenth of a unit.
  const SKINS = [
    { name: 'Mint Hoodie', skin: '#e8c39e', hair: '#1f6b5a', eye: '#27414a', top: '#5fd0a0', trim: '#3fa57c', legs: '#2c5a55', shoes: '#1d3b38', sleeve: 10, detail: 'pocket' },
    { name: 'Ember', skin: '#dfa27c', hair: '#7a2318', eye: '#3a2219', top: '#c94a2c', trim: '#e9c46a', legs: '#4a2620', shoes: '#2b1512', sleeve: 4, detail: 'stripe' },
    { name: 'Royal', skin: '#d8ab86', hair: '#2b1d5c', eye: '#241a4d', top: '#7650d6', trim: '#f0cf6b', legs: '#30245e', shoes: '#1c1438', sleeve: 12, detail: 'sash' },
    { name: 'Ashen', skin: '#c9a184', hair: '#2f3136', eye: '#1e2024', top: '#787d86', trim: '#a9afb9', legs: '#484c54', shoes: '#26282d', sleeve: 12, detail: 'zip' },
    { name: 'Nightfall', skin: '#231a33', hair: '#120d1c', eye: '#c79bff', top: '#1b1429', trim: '#8a5cf0', legs: '#151020', shoes: '#3d2a6b', sleeve: 12, detail: 'stripe', white: '#c79bff' },
  ];

  // The mouth is the skin tone, a few shades down.
  for (const s of SKINS) s.mouth = `#${new THREE.Color(s.skin).multiplyScalar(0.55).getHexString()}`;

  const head = (s, face, x, y) => {
    if (face === 'top') return s.hair;
    if (face === 'bottom') return s.skin;
    if (face === 'back') return y < 6 ? s.hair : s.skin;
    if (face === 'right' || face === 'left') {
      const toBack = face === 'right' ? x : 7 - x;
      return y < 2 || (y < 5 && toBack > 3) ? s.hair : s.skin;
    }
    if (y < 2 || (y === 2 && (x === 0 || x === 7))) return s.hair;
    if (y === 4 && (x === 1 || x === 6)) return s.white || '#f4f1ea';
    if (y === 4 && (x === 2 || x === 5)) return s.eye;
    if (y === 6 && x > 2 && x < 5) return s.mouth;
    return s.skin;
  };

  const torso = (s, face, x, y) => {
    if (face === 'top') return s.top;
    if (face === 'bottom' || y === 11) return s.legs;
    if (face === 'front') {
      if (y === 0 && x > 2 && x < 5) return s.skin;
      if (s.detail === 'pocket' && y > 6 && y < 10 && x > 1 && x < 6) return s.trim;
      if (s.detail === 'zip' && (x === 3 || x === 4)) return s.trim;
      if (s.detail === 'sash' && (x + y === 8 || x + y === 9)) return s.trim;
    }
    if (s.detail === 'stripe' && (y === 4 || y === 5)) return s.trim;
    return s.top;
  };

  const arm = (s, face, x, y) => {
    if (face === 'top') return s.top;
    if (face === 'bottom' || y >= s.sleeve) return s.skin;
    return y === s.sleeve - 1 ? s.trim : s.top;
  };

  const leg = (s, face, x, y) => {
    if (face === 'top') return s.legs;
    return face === 'bottom' || y > 8 ? s.shoes : s.legs;
  };

  // No two pixels of one colour are quite the same shade, as in a drawn skin.
  const grain = (x, y, seed) => {
    const n = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453;
    return 0.9 + (n - Math.floor(n)) * 0.17;
  };

  const tint = new THREE.Color();
  const texture = (width, height, seed, pixel) => {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const g = canvas.getContext('2d');
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        tint.set(pixel(x, y)).multiplyScalar(grain(x, y, seed));
        g.fillStyle = `#${tint.getHexString()}`;
        g.fillRect(x, y, 1, 1);
      }
    }
    const map = new THREE.CanvasTexture(canvas);
    map.magFilter = THREE.NearestFilter;
    map.minFilter = THREE.NearestFilter;
    map.generateMipmaps = false;
    map.colorSpace = THREE.SRGBColorSpace;
    return map;
  };

  // The order three.js gives the sides of a box.
  const FACES = ['right', 'left', 'top', 'bottom', 'front', 'back'];
  const BOX = { head: [8, 8, 8], torso: [8, 12, 4], arm: [4, 12, 4], leg: [4, 12, 4] };
  const PAINT = { head, torso, arm, leg };
  const PX = 0.1;

  const wardrobe = SKINS.map((skin, index) => {
    const maps = {};
    for (const part of Object.keys(BOX)) {
      const [w, h, d] = BOX[part];
      maps[part] = FACES.map((face, side) => {
        const upright = face !== 'top' && face !== 'bottom';
        const across = face === 'right' || face === 'left' ? d : w;
        return texture(across, upright ? h : d, index * 31 + side * 7 + w + h, (x, y) => PAINT[part](skin, face, x, y));
      });
    }
    return maps;
  });

  /* ------------------------------------------------------------- player */

  /** A player model. With clipping planes it carries one layer per plane. */
  const player = (planes = [null]) => {
    const group = new THREE.Group();
    const parts = [];
    const add = (part, parent, y) => {
      const [w, h, d] = BOX[part];
      const geometry = new THREE.BoxGeometry(w * PX, h * PX, d * PX);
      const layers = planes.map((plane) => {
        const materials = FACES.map(
          (_, side) => new THREE.MeshStandardMaterial({ map: wardrobe[0][part][side], roughness: 0.92, clippingPlanes: plane ? [plane] : null }),
        );
        const mesh = new THREE.Mesh(geometry, materials);
        mesh.position.y = y;
        parent.add(mesh);
        return { mesh, materials };
      });
      parts.push({ part, layers });
    };
    const joint = (x, y) => {
      const pivot = new THREE.Group();
      pivot.position.set(x, y, 0);
      group.add(pivot);
      return pivot;
    };

    const joints = { head: joint(0, 2.4), armL: joint(-0.6, 2.3), armR: joint(0.6, 2.3), legL: joint(-0.2, 1.2), legR: joint(0.2, 1.2) };
    add('head', joints.head, 0.4);
    add('torso', group, 1.8);
    add('arm', joints.armL, -0.5);
    add('arm', joints.armR, -0.5);
    add('leg', joints.legL, -0.6);
    add('leg', joints.legR, -0.6);

    const wear = (layer, skin) => {
      for (const { part, layers } of parts) {
        layers[layer].materials.forEach((material, side) => {
          material.map = wardrobe[skin][part][side];
        });
      }
    };
    const show = (layer, visible) => {
      for (const { layers } of parts) layers[layer].mesh.visible = visible;
    };
    return { group, joints, wear, show };
  };

  const root = new THREE.Group();
  frame.add(root);
  root.add(stage.halo(stage.accent, 7.5, 0.2));
  const turn = new THREE.Group();
  turn.position.y = -1.6;
  root.add(turn);

  // What is still the old skin lies below the scan, the new one above it.
  const below = new THREE.Plane();
  const above = new THREE.Plane();
  const me = player([below, above]);
  me.show(1, false);
  turn.add(me.group);

  const others = [
    { skin: 3, x: -2.3, z: -0.9 },
    { skin: 1, x: 1.9, z: -1.55 },
  ].map(({ skin, x, z }) => {
    const other = player();
    other.wear(0, skin);
    other.group.scale.setScalar(0.4);
    other.group.position.set(x, 0, z);
    other.group.rotation.y = Math.atan2(-x, -z);
    // Something to stand on.
    const spot = new THREE.Mesh(new THREE.TorusGeometry(0.42, 0.008, 8, 64), stage.mat.glow(stage.accent, { opacity: 0.45 }));
    spot.rotation.x = Math.PI / 2;
    spot.position.set(x, 0, z);
    turn.add(other.group, spot);
    return { ...other, x, z, spot, distance: Math.hypot(x, z), hop: 0 };
  });

  /* -------------------------------------------------------------- floor */

  const floor = new THREE.Mesh(
    new THREE.CylinderGeometry(1.25, 1.25, 0.06, 64),
    stage.mat.solid(0x0c120e, { roughness: 0.35, metalness: 0.6 }),
  );
  floor.position.y = -0.035;
  const rim = new THREE.Mesh(new THREE.TorusGeometry(1.25, 0.012, 8, 96), stage.mat.glow(stage.accent, { opacity: 0.8 }));
  rim.rotation.x = Math.PI / 2;
  turn.add(floor, rim);

  const rings = [0, 1, 2].map(() => {
    const ring = new THREE.Mesh(new THREE.RingGeometry(0.975, 1, 96), stage.mat.glow(stage.accentHi, { opacity: 0, side: THREE.DoubleSide }));
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = 0.01;
    turn.add(ring);
    return ring;
  });

  // The scan itself: a square of light around the body.
  const scan = new THREE.Group();
  const square = new THREE.Shape();
  square.moveTo(-0.85, -0.5).lineTo(0.85, -0.5).lineTo(0.85, 0.5).lineTo(-0.85, 0.5).closePath();
  const sheet = new THREE.Mesh(new THREE.ShapeGeometry(square), stage.mat.glow(stage.accent, { opacity: 0.3, side: THREE.DoubleSide }));
  const border = new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(square.getPoints()), stage.mat.line(stage.accentHi, 1));
  scan.add(sheet, border);
  scan.rotation.x = -Math.PI / 2;
  scan.visible = false;
  turn.add(scan);

  let label = '';
  const plate = stage.plate(3.8, 0.36, (g, w, h) => {
    g.fillStyle = '#f2f5f2';
    g.font = stage.font.mono(Math.round(h * 0.42));
    g.textBaseline = 'middle';
    g.textAlign = 'center';
    g.fillText(label, w / 2, h / 2);
  });
  plate.position.set(0, -2.25, 1.2);
  frame.add(plate);

  // On a phone the scene sits above the headline: no room for the label, and
  // the two at the back move in so they stay in the picture.
  const fit = (view) => {
    plate.visible = stage.mode === 'hero' && view.wide;
    const near = view.wide ? 1 : 0.74;
    for (const other of others) {
      other.group.position.x = other.x * near;
      other.spot.position.x = other.x * near;
    }
  };
  stage.onResize(fit);
  fit(stage.view);

  stage.dust({ count: 150, radius: 6.5, color: stage.accentHi, opacity: 0.45 });

  /* ----------------------------------------------------------- behaviour */

  const TOP = 3.3;
  const DOWN = new THREE.Vector3(0, -1, 0);
  const UP = new THREE.Vector3(0, 1, 0);
  let wearing = 0;
  let next = 0;
  let sweep = -1;
  let pulse = -1;
  let idle = 0;

  const say = (text) => {
    if (text === label) return;
    label = text;
    plate.userData.redraw();
  };
  say(`Equipped ${SKINS[0].name}`);

  const swap = () => {
    if (sweep >= 0) return;
    next = (wearing + 1) % SKINS.length;
    me.wear(1, next);
    me.show(1, true);
    sweep = 0;
    idle = 0;
    say(`Equipping ${SKINS[next].name}…`);
  };
  stage.onClick(swap);

  stage.onFrame((time, delta) => {
    root.rotation.x = 0.12 + stage.pitch;
    root.position.y = 0.2 + Math.sin(time * 0.7) * 0.04;
    turn.rotation.y = -0.5 + stage.yaw * 1.5;

    // Standing, not frozen: the arms sway and the head follows the pointer.
    me.joints.armL.rotation.x = Math.sin(time * 1.5) * 0.1;
    me.joints.armR.rotation.x = -Math.sin(time * 1.5) * 0.1;
    me.joints.armL.rotation.z = -0.04;
    me.joints.armR.rotation.z = 0.04;
    me.joints.head.rotation.y = stage.pointer.x * 0.45 + 0.2;
    me.joints.head.rotation.x = -stage.pointer.y * 0.25;

    idle += delta;
    if (idle > 5.5) swap();

    let height = 100;
    if (sweep >= 0) {
      sweep = Math.min(1, sweep + delta / 1.25);
      const eased = sweep * sweep * (3 - 2 * sweep);
      height = TOP - eased * (TOP + 0.1);
      scan.position.y = height;
      if (sweep === 1) {
        wearing = next;
        me.wear(0, wearing);
        me.show(1, false);
        sweep = -1;
        height = 100;
        pulse = 0;
        say(`Equipped ${SKINS[wearing].name} — synced live`);
      }
    }
    scan.visible = sweep >= 0;

    // Clipping planes live in world space; the model does not.
    turn.updateWorldMatrix(true, false);
    below.set(DOWN, height).applyMatrix4(turn.matrixWorld);
    above.set(UP, -height).applyMatrix4(turn.matrixWorld);

    // The swap travels outwards; whoever it reaches looks up.
    if (pulse >= 0) pulse += delta / 1.9;
    rings.forEach((ring, index) => {
      const p = pulse < 0 ? -1 : pulse - index * 0.16;
      const on = p > 0 && p < 1;
      ring.visible = on;
      if (!on) return;
      ring.scale.setScalar(1.25 + p * 3.4);
      ring.material.opacity = (1 - p) * 0.6;
    });
    if (pulse > 1.6) pulse = -1;
    for (const other of others) {
      const reach = pulse < 0 ? 0 : (1.25 + pulse * 3.4) / other.distance;
      if (reach > 1 && reach < 1.25 && other.hop <= 0) other.hop = 1;
      other.hop = Math.max(0, other.hop - delta * 1.8);
      other.group.position.y = Math.sin((1 - other.hop) * Math.PI) * 0.16 * (other.hop > 0 ? 1 : 0);
      other.joints.head.rotation.x = -0.28 * Math.sin((1 - other.hop) * Math.PI) * (other.hop > 0 ? 1 : 0) - 0.12;
      other.joints.armL.rotation.x = Math.sin(time * 1.3 + other.distance) * 0.08;
      other.joints.armR.rotation.x = -Math.sin(time * 1.3 + other.distance) * 0.08;
    }
  });
}
