package com.listingclown3.development.Path;

import net.minecraft.util.BlockPos;

public class Node implements Comparable<Node> {
    public BlockPos pos;
    public Node parent;
    public double f;
    public double g;
    public double h;

    public Node(BlockPos pos, Node parent, double g, double h) {
        this.pos = pos;
        this.parent = parent;
        this.g = g;
        this.h = h;
        this.f = g + h;
    }

    @Override
    public int compareTo(Node other) {
        if (this.f < other.f) {
            return -1;
        } else if (this.f > other.f) {
            return 1;
        } else {
            return 0;
        }
    }
}


