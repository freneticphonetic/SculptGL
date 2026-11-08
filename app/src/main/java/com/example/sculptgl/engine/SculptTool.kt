package com.example.sculptgl.engine

enum class SculptTool(val displayName: String, val description: String) {
    BRUSH("Brush", "Standard clay brush that builds up or carves surface"),
    PLANAR("Planar", "Flattens geometry into crisp hard-surface planes & facets"),
    INFLATE("Inflate", "Expands or deflates vertices along their normals"),
    TWIST("Twist", "Rotates vertices around the contact normal"),
    SMOOTH("Smooth", "Averages vertex positions to soften details"),
    FLATTEN("Flatten", "Projects vertices toward the local plane"),
    PINCH("Pinch", "Attracts vertices toward the center of the brush"),
    CREASE("Crease", "Carves sharp grooves and creases into the mesh"),
    DRAG("Drag", "Pulls vertices dynamically along screen drag vector"),
    PAINT("Paint", "Paints vertex color on the mesh surface"),
    MOVE("Move", "Grabs a larger region and translates it in 3D"),
    MASK("Mask", "Paints mask protection to freeze vertices from deformation")
}
