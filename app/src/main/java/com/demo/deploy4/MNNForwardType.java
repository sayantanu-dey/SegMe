package com.demo.deploy4;

public enum MNNForwardType {
    /**
     * CPU
     */
    FORWARD_CPU(0),
    /**
     * OPENCL
     */
    FORWARD_OPENCL(3),
    /**
     * VULKAN
     */
    FORWARD_VULKAN(7);

    public int type;

    MNNForwardType(int t) {
        type = t;
    }
}
