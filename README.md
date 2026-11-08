# SculptGL 3D for Android

A 3D digital sculpting and modeling application for Android, built with Kotlin, Jetpack Compose, and OpenGL ES 2.0/3.0. Ported and optimized for mobile touch devices from Stéphane Ginier's SculptGL.

## Features

- **3D Sculpting Engine**:
  - **Planar Brush**: Chisel and flatten geometry into crisp planar facets & hard surfaces (requested for architectural & stylized sculpting).
  - **Standard Tools**: Brush (Clay), Inflate, Twist, Smooth (Laplacian relaxation), Flatten, Pinch, Crease, Drag, Paint (vertex coloring), Move, and Mask.
  - **Symmetry**: Real-time mirror deformation across the X-axis.
  - **Sub / Add Mode**: Toggle between carving into and building up surface with quick button or sliders.
  - **Adjustable Brush Dynamics**: Real-time radius and intensity control with projected 3D cursor ring.

- **Mobile Touch Gestures**:
  - **✌️ Two-Finger Tap**: Instant Undo (standard Android digital art gesture).
  - **🖐️ Three-Finger Tap**: Instant Redo (standard Android digital art gesture).
  - **☝️ Single-Finger Touch**: Sculpt directly on model; touch empty canvas to orbit camera.
  - **🤏 Pinch-to-Zoom**: Two-finger pinch to zoom in/out smoothly.
  - **✌️ Two-Finger Drag**: Two-finger drag to pan the 3D viewport.

- **3D Primitives & Modeling**:
  - Normalized Quad-Sphere (uniform vertex distribution without polar pinch)
  - Subdivided Cube
  - Cylinder with end caps
  - Torus
  - Relief Plane
  - Dynamic 1-to-4 triangle subdivision for detail enhancement

- **Rendering & Matcaps**:
  - Authentic SculptGL Matcaps: Clay, Red Clay, Pearl, Green, Skin, White
  - Directional Blinn-Phong studio lighting
  - Normal map visualization
  - Wireframe overlay toggle
  - Grid floor toggle

- **Import & Export**:
  - Export Wavefront OBJ (`.obj`) with vertex colors
  - Export 3D Print STL (`.stl` binary format)
  - Android Share Sheet integration via FileProvider
