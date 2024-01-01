package com.listingclown3.development;

import com.listingclown3.development.Path.Node;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.*;

import static net.minecraft.client.Minecraft.getMinecraft;

@Mod(modid = Main.MODID, version = Main.VERSION)
public class Main {
    public static final String MODID = "Pathfinder";
    public static final String VERSION = "1.0";
    private List<BlockPos> linePositions = new ArrayList();
    private KeyBinding setStartPosKeyDijkstra = new KeyBinding("Set Start Position (Dijkstra)", Keyboard.KEY_O, "NotEnoughFrags");
    private KeyBinding setEndPosKeyDijkstra = new KeyBinding("Set End Position (Dijkstra)", Keyboard.KEY_P, "NotEnoughFrags");
    private KeyBinding clearPathKey = new KeyBinding("Clear Path", Keyboard.KEY_I, "NotEnoughFrags");
    private KeyBinding followPathKey = new KeyBinding("Follow Path", Keyboard.KEY_F, "NotEnoughFrags");
    private KeyBinding showSpeedKey = new KeyBinding("Show Speed", Keyboard.KEY_H, "NotEnoughFrags");
    private List<BlockPos> groupPositions = null;
    private BlockPos startPosDijkstra = null;
    private BlockPos endPosDijkstra = null;
    private Thread pathfindingThread;
    private Thread speedThread;
    private Thread linedevThread;
    private volatile List<BlockPos> pathDijkstra;
    private List<BlockPos> cubePositions = new ArrayList();
    private int currentPathIndex = 0;
    private  boolean isFollowingPath = false;
    private boolean isInRayTracingPath = false;
    private double currentLineDirectionX = 0;
    private double currentLineDirectionZ = 0;
    private volatile boolean shouldPauseMovement = false; // So that line deviation can catch up with player movement if it strays too far from path.
    private boolean pathExists = false;
    private BlockPos startedPos = null;
    private BlockPos endedPos = null;
    private BlockPos tempEndPos = null;
    private Map<Integer, BlockPos> lastKnownPositions = new HashMap();

    public void print(String message) {
        System.out.println("[" + MODID + "]" + ": " + message);

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        print("Initialized " + MODID);

        ClientRegistry.registerKeyBinding(setStartPosKeyDijkstra);
        ClientRegistry.registerKeyBinding(setEndPosKeyDijkstra);
        ClientRegistry.registerKeyBinding(clearPathKey);
        ClientRegistry.registerKeyBinding(followPathKey);
        ClientRegistry.registerKeyBinding(showSpeedKey);

        MinecraftForge.EVENT_BUS.register(this);

    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) throws AWTException {

        if (event.phase == TickEvent.Phase.END) {

            final Minecraft mc = getMinecraft();

            if (mc != null) {

                final World world = mc.theWorld;
                final EntityPlayer player = mc.thePlayer;

                if (player != null) {

                    if (setStartPosKeyDijkstra.isPressed()) {

                        if (pathExists) {

                            player.addChatMessage(new ChatComponentText(": " + "Cannot change start path while path is created..."));

                        }

                        // Set the start position for Dijkstra's algorithm to the block the player is looking at
                        MovingObjectPosition mop = player.rayTrace(20000, 1.0F);
                        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                            startPosDijkstra = mop.getBlockPos();
                            player.addChatMessage(new ChatComponentText(": " + "Start position set for Dijkstra's algorithm..."));
                        }

                    } else if (setEndPosKeyDijkstra.isPressed()) {

                        // Bug fix
                        if (pathExists) {
                            startPosDijkstra = player.getPosition();
                            endPosDijkstra = null;
                            pathDijkstra = null;
                            currentPathIndex = 0;
                            if (cubePositions != null) {
                                cubePositions.clear();
                            }

                            MovingObjectPosition mop = player.rayTrace(20000, 1.0F);
                            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                                endPosDijkstra = mop.getBlockPos();
                                endedPos = mop.getBlockPos();
                                player.addChatMessage(new ChatComponentText(": " + "End position set for Dijkstra's algorithm..."));
                                pathExists = true;
                            }

                        }

                        endPosDijkstra = null;

                        if (cubePositions != null) {
                            cubePositions.clear();
                        }

                        // Set the end position for Dijkstra's algorithm to the block the player is looking at
                        MovingObjectPosition mop = player.rayTrace(20000, 1.0F);
                        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                            endPosDijkstra = mop.getBlockPos();
                            player.addChatMessage(new ChatComponentText(": " + "End position set for Dijkstra's algorithm..."));
                        }
                    } else if (clearPathKey.isPressed()) {
                        // Clear the path and reset the start and end positions
                        try {

                        if (pathfindingThread != null && pathfindingThread.isAlive()) {
                            // Wait for the pathfinding thread to finish
                            try {
                                pathfindingThread.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        isFollowingPath = false;
                        pathDijkstra = null;
                        startPosDijkstra = null;
                        endPosDijkstra = null;
                        currentPathIndex = 0;


                        if (cubePositions != null) {
                            cubePositions.clear();
                        }

                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);

                        player.addChatMessage(new ChatComponentText(": " + "Path cleared..."));



                        } catch (Exception e) {
                            e.printStackTrace();

                        }

                    }

                    if (followPathKey.isPressed()) {
                        isFollowingPath = !isFollowingPath;
                        player.addChatMessage(new ChatComponentText("Following State: [" + isFollowingPath + "]"));
                    }

                    if (isFollowingPath) {
                        if (cubePositions != null && !cubePositions.isEmpty()) {
                            if (!isInRayTracingPath) {
                            if (currentPathIndex >= cubePositions.size()) {
                                // handle the case where currentPathIndex is out of bounds
                                // for example, you could stop following the path and reset currentPathIndex

                                currentPathIndex = 0;
                                pathDijkstra = null;
                                startPosDijkstra = null;
                                endPosDijkstra = null;

                                if (cubePositions != null) {
                                    cubePositions.clear();
                                }

                                isFollowingPath = false;

                            } else {

                                BlockPos nextPos = null;
                                if (currentPathIndex >= 0 && currentPathIndex < cubePositions.size()) {
                                    nextPos = cubePositions.get(currentPathIndex);
                                }
                                double diffX = nextPos.getX() + 0.5 - player.posX;
                                double diffZ = nextPos.getZ() + 0.5 - player.posZ;

                                // Check if this is a new straight line segment
                                if (currentLineDirectionX == 0 && currentLineDirectionZ == 0) {
                                    // Set the direction of the current straight line segment
                                    currentLineDirectionX = diffX;
                                    currentLineDirectionZ = diffZ;
                                }

                                float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                                player.rotationYaw = yaw;

                                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);

                                // Check if this is a turning point
                                if (Math.abs(diffX) < 1 && Math.abs(diffZ) < 1) {
                                    // Reset the direction of the current straight line segment
                                    currentLineDirectionX = 0;
                                    currentLineDirectionZ = 0;

                                    // Move to the next position in the path
                                    currentPathIndex++;
                                    if (currentPathIndex >= cubePositions.size() && player.getPosition().equals(endPosDijkstra)) {

                                        if (pathfindingThread != null && pathfindingThread.isAlive()) {
                                            // Wait for the pathfinding thread to finish
                                            try {
                                                pathfindingThread.join();
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        pathDijkstra = null;
                                        startPosDijkstra = null;
                                        endPosDijkstra = null;
                                        currentPathIndex = 0;
                                        isFollowingPath = false;

                                        if (cubePositions != null) {
                                            cubePositions.clear();
                                        }

                                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);

                                        player.addChatMessage(new ChatComponentText(": " + "Path cleared..."));


                                    }
                                }

                            }
                        } else {
                                endPosDijkstra = tempEndPos;
                                isInRayTracingPath = false;
                                getMinecraft().thePlayer.addChatComponentMessage(new ChatComponentText("Changed endPosDijkstra to " + tempEndPos));
                            }
                        }
                    } else {

                        if (!Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                        }
                        if (!Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode())) {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                        }


                    }
                }

                // Check if the next position in the path is too close to the current position
                if (currentPathIndex < cubePositions.size() - 1) {
                    if (player != null) {
                        BlockPos nextPos = cubePositions.get(currentPathIndex + 1);
                        double nextDiffX = nextPos.getX() + 0.5 - player.posX;
                        double nextDiffZ = nextPos.getZ() + 0.5 - player.posZ;
                        double nextDistance = Math.sqrt(nextDiffX * nextDiffX + nextDiffZ * nextDiffZ);

                        if (nextDistance < 1) {
                            // The next position is too close, so skip it
                            currentPathIndex++;
                        }
                    }
                }

                if (showSpeedKey.isPressed()) {

                    speedThread = new Thread(new Runnable() {


                        @Override
                        public void run() {

                            EntityPlayer player = getMinecraft().thePlayer;

                            double lastX = player.posX;
                            double lastY = player.posY;
                            double lastZ = player.posZ;

                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            double currentX = player.posX;
                            double currentY = player.posY;
                            double currentZ = player.posZ;

                            double distance = Math.sqrt(Math.pow(currentX - lastX, 2) + Math.pow(currentY - lastY, 2) + Math.pow(currentZ - lastZ, 2));

                            double speed = distance / 3;

                            player.addChatMessage(new ChatComponentText("Your speed is: " + speed));

                        }
                    });

                    speedThread.start();


                }
                linedevThread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        checkForLineDeviation(world, player);

                    }
                });
                linedevThread.start();


                // Find and draw the path using Dijkstra's algorithm if both start and end positions are set
                if (startPosDijkstra != null && endPosDijkstra != null) {
                    // Check if the pathfinding thread is already running
                    if (pathfindingThread == null || !pathfindingThread.isAlive()) {
                        // Create a new pathfinding thread
                        pathfindingThread = new Thread() {
                            @Override
                            public void run() {
                                // Run the pathfinding algorithm on this thread

                                pathDijkstra = findPathDijkstra(world, startPosDijkstra, endPosDijkstra);

                            }
                        };
                        pathfindingThread.start();
                    }

                }


            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = getMinecraft();
        EntityPlayer player = mc.thePlayer;
        World world = mc.theWorld;

        // Set the fixed position here
        double x = 0;
        double y = 0;
        double z = 0;

        if (mc.theWorld != null && mc.thePlayer != null) {
            // Get the player's current position
            double playerX = mc.thePlayer.posX;
            double playerY = mc.thePlayer.posY;
            double playerZ = mc.thePlayer.posZ;

            // Iterate through all loaded entities

            // Set up OpenGL for rendering
            GL11.glPushMatrix();
            GL11.glTranslated(-TileEntityRendererDispatcher.staticPlayerX, -TileEntityRendererDispatcher.staticPlayerY, -TileEntityRendererDispatcher.staticPlayerZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(5.0F);

        }


        drawPath(pathDijkstra, 0.0F, 1.0F, 0.0F); // Draw Dijkstra's path in green

        // Render the lines
        if (linePositions.size() > 1) {
            double lineStartX = linePositions.get(0).getX() + 0.5;
            double lineStartY = linePositions.get(0).getY() + 1;
            double lineStartZ = linePositions.get(0).getZ() + 0.5;
            for (int i = 1; i < linePositions.size(); i++) {
                BlockPos linePos = linePositions.get(i);
                double lineEndX = linePos.getX() + 0.5;
                double lineEndY = linePos.getY() + 1;
                double lineEndZ = linePos.getZ() + 0.5;

                // Check if the line is within the render distance
                double dx = player.posX - (lineStartX + lineEndX) / 2;
                double dy = player.posY - (lineStartY + lineEndY) / 2;
                double dz = player.posZ - (lineStartZ + lineEndZ) / 2;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                int renderDistanceChunks = mc.gameSettings.renderDistanceChunks;
                if (distanceSquared <= renderDistanceChunks * renderDistanceChunks * 256) {
                    // The line is within the render distance, so render it
                    GL11.glColor4f(1.0F, 0.0F, 0.0F, 1.0F);
                    Tessellator tessellator = Tessellator.getInstance();
                    WorldRenderer worldrenderer = tessellator.getWorldRenderer();
                    worldrenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
                    worldrenderer.pos(lineStartX, lineStartY, lineStartZ).endVertex();
                    worldrenderer.pos(lineEndX, lineEndY, lineEndZ).endVertex();
                    tessellator.draw();
                } else {
                    // The line is outside the render distance, so remove it from the list of lines to be rendered
                    linePositions.remove(i);
                    i--;
                }

                // Update the starting position of the next line
                lineStartX = lineEndX;
                lineStartY = lineEndY;
                lineStartZ = lineEndZ;
            }
        }

        // Reset OpenGL state
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

    }

    private void drawPath(List<BlockPos> path, float r, float g, float b) {
        if (path != null && path.size() > 1) {

            cubePositions.clear();

            double lineStartX = path.get(0).getX() + 0.5;
            double lineStartY = path.get(0).getY() + 1.2;
            double lineStartZ = path.get(0).getZ() + 0.5;
            for (int i = 1; i < path.size(); i++) {
                BlockPos linePos = path.get(i);
                double lineEndX = linePos.getX() + 0.5;
                double lineEndY = linePos.getY() + 1.2;
                double lineEndZ = linePos.getZ() + 0.5;

                // Check if this is part of a 90-degree turn
                if (i < path.size() - 1) {
                    BlockPos nextLinePos = path.get(i + 1);
                    double nextLineEndX = nextLinePos.getX() + 0.5;
                    double nextLineEndY = nextLinePos.getY() + 1.2;
                    double nextLineEndZ = nextLinePos.getZ() + 0.5;

                    if ((lineStartX == lineEndX && lineEndZ == nextLineEndZ) || (lineStartZ == lineEndZ && lineEndX == nextLineEndX)) {
                        // This is part of a 90-degree turn, so adjust the starting and ending positions to draw a diagonal line instead
                        double midX = (lineEndX + nextLineEndX) / 2;
                        double midY = (lineEndY + nextLineEndY) / 2;
                        double midZ = (lineEndZ + nextLineEndZ) / 2;

                        // Draw a diagonal line from the starting position to the midpoint
                        GL11.glColor4f(r, g, b, 1.0F);
                        Tessellator tessellator = Tessellator.getInstance();
                        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
                        worldrenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
                        worldrenderer.pos(lineStartX, lineStartY, lineStartZ).endVertex();
                        worldrenderer.pos(midX, midY, midZ).endVertex();
                        tessellator.draw();

                        // Update the starting position of the next line to be the midpoint
                        lineStartX = midX;
                        lineStartY = midY;
                        lineStartZ = midZ;

                        // Skip drawing the current line segment
                        continue;
                    }
                }
                Tessellator tessellator = Tessellator.getInstance();
                WorldRenderer worldrenderer = tessellator.getWorldRenderer();

                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                // Draw cube at lineStart
                worldrenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();

                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();

                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY - 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX + 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();

                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ - 0.05).endVertex();
                worldrenderer.pos(lineStartX - 0.05, lineStartY + 0.05, lineStartZ + 0.05).endVertex();
                tessellator.draw();

                // Draw cube at lineEnd
                worldrenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();

                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();

                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY - 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX + 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();

                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ - 0.05).endVertex();
                worldrenderer.pos(lineEndX - 0.05, lineEndY + 0.05, lineEndZ + 0.05).endVertex();
                tessellator.draw();


                GL11.glColor4f(r, g, b, 1.0F);
                worldrenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
                worldrenderer.pos(lineStartX, lineStartY, lineStartZ).endVertex();
                worldrenderer.pos(lineEndX, lineEndY, lineEndZ).endVertex();
                tessellator.draw();

                // Store the coordinates of the cubes for later use
                BlockPos startPos = new BlockPos(lineStartX, lineStartY, lineStartZ);
                BlockPos endPos = new BlockPos(lineEndX, lineEndY, lineEndZ);
                if (!cubePositions.contains(startPos)) {
                    cubePositions.add(startPos);
                }
                if (!cubePositions.contains(endPos)) {
                    cubePositions.add(endPos);
                }

                // Prevents duplicate coordinates which can lead to infinite recursion and disables the ability to stop moving

                // Update the starting position of the next line
                lineStartX = lineEndX;
                lineStartY = lineEndY;
                lineStartZ = lineEndZ;

            }
        }
    }

    private double getDistance(BlockPos pos1, BlockPos pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public List<BlockPos> findPathDijkstra(final World world, final BlockPos start, final BlockPos end) {
        // Create the open and closed lists
        PriorityQueue<Node> openList = new PriorityQueue<Node>();
        HashSet<BlockPos> closedList = new HashSet<BlockPos>();

        // Add the starting node to the open list
        Node startNode = new Node(start, null, 0, 0);
        openList.add(startNode);

        // Loop until the open list is empty or the end node is reached
        while (!openList.isEmpty()) {
            // Find the node with the lowest f value in the open list
            Node currentNode = openList.poll();

            // Move the current node from the open list to the closed list
            closedList.add(currentNode.pos);

            // Check if the end node has been reached
            if (currentNode.pos.equals(end)) {
                // Construct the path by tracing back from the end node to the start node
                List<BlockPos> path = new ArrayList<BlockPos>();
                Node node = currentNode;
                while (node != null) {
                    path.add(0, node.pos);
                    node = node.parent;
                }
                return path;
            }


            // Generate the neighbors of the current node
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        BlockPos neighborPos = currentNode.pos.add(x, y, z);
                        IBlockState blockState = world.getBlockState(neighborPos);

                        double g = currentNode.g + getDistance(currentNode.pos, neighborPos);

                        // Check if the block is solid
                        if (!blockState.getBlock().getMaterial().isSolid()) continue;

                        // Check if the block is solid or a stair, slab or carpet
                        Block block = blockState.getBlock();
                        boolean isStairOrSlabOrCarpet = block instanceof BlockStairs || block instanceof BlockSlab || (!(block instanceof BlockCarpet));
                        if (!block.getMaterial().isSolid() && !isStairOrSlabOrCarpet) continue;


                        // Check if the two blocks above this block are passable, so the player doesn't run into it.
                        BlockPos blockAbove1 = neighborPos.up();
                        BlockPos blockAbove2 = blockAbove1.up();
                        IBlockState blockStateAbove1 = world.getBlockState(blockAbove1);
                        IBlockState blockStateAbove2 = world.getBlockState(blockAbove2);
                        AxisAlignedBB collisionBoxAbove1 = blockStateAbove1.getBlock().getCollisionBoundingBox(world, blockAbove1, blockStateAbove1);
                        AxisAlignedBB collisionBoxAbove2 = blockStateAbove2.getBlock().getCollisionBoundingBox(world, blockAbove2, blockStateAbove2);

                        // Allow navigation over carpets even if there is not enough clearance above them
                        Block blockAbove = world.getBlockState(neighborPos.up()).getBlock();
                        if (block instanceof BlockCarpet || blockAbove instanceof BlockCarpet) {
                            collisionBoxAbove1 = null;
                            collisionBoxAbove2 = null;
                        }

                        if (collisionBoxAbove1 != null || collisionBoxAbove2 != null) continue;

                        // Check if the neighbor block is one block higher than the current block
                        if (neighborPos.getY() == currentNode.pos.getY() + 1) {
                            // Check if the neighbor block is a stair or slab
                            boolean isStairOrSlab = block instanceof BlockStairs || block instanceof BlockSlab;
                            // Check if the neighbor block is not a stair or slab
                            if (!isStairOrSlab) continue;
                        }

                        // Check if this is a diagonal neighbor and if there is a clear path between this neighbor and currentNode
                        if (x != 0 && z != 0) {
                            boolean isBlocked = false;
                            for (int i = 0; i < 4; i++) {
                                BlockPos adjacentNeighbor1 = currentNode.pos.add(x, i, 0);
                                BlockPos adjacentNeighbor2 = currentNode.pos.add(0, i, z);
                                IBlockState adjacentBlockState1 = world.getBlockState(adjacentNeighbor1);
                                IBlockState adjacentBlockState2 = world.getBlockState(adjacentNeighbor2);

                                // Check if either of the adjacent blocks is solid
                                if (adjacentBlockState1.getBlock().getMaterial().isSolid() || adjacentBlockState2.getBlock().getMaterial().isSolid()) {
                                    isBlocked = true;
                                    break;
                                }
                            }

                            // Calculate the distance from this neighbor to the nearest wall or obstacle within a 2 block radius
                            double minDistanceToWall = Double.MAX_VALUE;
                            for (int i = -2; i <= 2; i++) {
                                for (int j = -2; j <= 2; j++) {
                                    for (int k = -2; k <= 2; k++) {
                                        if (i == 0 && j == 0 && k == 0) continue;

                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                        // Check if the adjacent block is solid
                                        if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                            double distanceToWall = Math.sqrt(i * i + j * j + k * k);
                                            minDistanceToWall = Math.min(minDistanceToWall, distanceToWall);
                                        }
                                    }
                                }
                            }

                            // Add a penalty to the g value of this neighbor based on its distance to the nearest wall or obstacle
                            g += 2 / minDistanceToWall;

                            // Check if this neighbor is adjacent to a wall or block corner and adjust its position to space out the navigation
                            boolean isAdjacentToWallOrCorner = false;
                            boolean isAdjacentToLeftOrRight = false;
                            boolean isAdjacentToWall = false;

                            // Check if this neighbor is within a certain distance of a wall
                            int maxDistance = 3;
                            double minDistanceToWalls = Double.MAX_VALUE;
                            for (int i = -maxDistance; i <= maxDistance; i++) {
                                for (int j = -maxDistance; j <= maxDistance; j++) {
                                    for (int k = -maxDistance; k <= maxDistance; k++) {
                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                        // Check if the adjacent block is solid
                                        if (adjacentMaterial.isSolid()) {
                                            double distanceToWall = Math.sqrt(i * i + j * j + k * k);
                                            minDistanceToWalls = Math.min(minDistanceToWalls, distanceToWall);
                                        }
                                    }
                                }
                            }

                            // Add a penalty to the g value of this neighbor based on its distance to the nearest wall
                            if (minDistanceToWalls < maxDistance) {
                                g += (maxDistance - minDistanceToWalls) * 5;
                            }

                            // Check if this is a diagonal neighbor and if there is a clear path between this neighbor and currentNode
                            if (x != 0 && z != 0) {
                                boolean isBlockeds = false;
                                for (int i = 0; i < 4; i++) {
                                    BlockPos adjacentNeighbor1 = currentNode.pos.add(x, i, 0);
                                    BlockPos adjacentNeighbor2 = currentNode.pos.add(0, i, z);
                                    IBlockState adjacentBlockState1 = world.getBlockState(adjacentNeighbor1);
                                    IBlockState adjacentBlockState2 = world.getBlockState(adjacentNeighbor2);

                                    // Check if either of the adjacent blocks is solid
                                    if (adjacentBlockState1.getBlock().getMaterial().isSolid() || adjacentBlockState2.getBlock().getMaterial().isSolid()) {
                                        isBlockeds = true;
                                        break;
                                    }
                                }

                                if (isBlockeds) continue;
                            }

                            // Essentially a border around you so that you don't bump into stuff :) (The collision algorithms in like 50% of the code just did not work)

                            if (isFollowingPath) {
                                // Get the player's eye position
                                Vec3 eyePos = new Vec3(getMinecraft().thePlayer.posX, getMinecraft().thePlayer.posY + getMinecraft().thePlayer.getEyeHeight(), getMinecraft().thePlayer.posZ);

                                // Calculate the direction the player is looking
                                Vec3 lookVec = getMinecraft().thePlayer.getLookVec();

                                // Calculate the directions to the left and right of the player
                                Vec3 leftVec = lookVec.rotateYaw(90);
                                Vec3 rightVec = lookVec.rotateYaw(-90);

                                // Perform the raytraces
                                MovingObjectPosition rayTraceResultForward = performRayTrace(eyePos, lookVec, world);
                                MovingObjectPosition rayTraceResultLeft = performRayTrace(eyePos, leftVec, world);
                                MovingObjectPosition rayTraceResultRight = performRayTrace(eyePos, rightVec, world);

                                // Check the results of the raytraces
                                checkRayTraceResult(rayTraceResultForward, world, getMinecraft().thePlayer);
                                checkRayTraceResult(rayTraceResultLeft, world, getMinecraft().thePlayer);
                                checkRayTraceResult(rayTraceResultRight, world, getMinecraft().thePlayer);
                            }

                            // Check if this neighbor is within a certain distance of a wall or corner
                            double minDistanceToWallsOrCorners = Double.MAX_VALUE;
                            for (int i = -maxDistance; i <= maxDistance; i++) {
                                for (int j = -maxDistance; j <= maxDistance; j++) {
                                    for (int k = -maxDistance; k <= maxDistance; k++) {
                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                        // Check if the adjacent block is solid
                                        if (adjacentMaterial.isSolid()) {
                                            double distanceToWallOrCorner = Math.sqrt(i * i + j * j + k * k);
                                            minDistanceToWallsOrCorners = Math.min(minDistanceToWallsOrCorners, distanceToWallOrCorner);
                                        }
                                    }
                                }
                            }

                            // Check if this neighbor collides with any solid blocks
                            boolean isCollidingWithSolidBlock = false;
                            for (int i = 0; i < 2; i++) {
                                for (int j = -3; j <= 3; j++) {
                                    for (int k = -3; k <= 3; k++) {
                                        BlockPos adjacentNeighbor = currentNode.pos.add(j, i, k);
                                        IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                        // Check if the adjacent block is solid
                                        if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                            isCollidingWithSolidBlock = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isCollidingWithSolidBlock) {
                                // Add additional cube positions around the turn to space out the turn and avoid collision
                                int numAdditionalCubes = 3;
                                for (int i = 1; i <= numAdditionalCubes; i++) {
                                    double t = (double) i / (numAdditionalCubes + 1);
                                    double newX = currentNode.pos.getX() + t * (neighborPos.getX() - currentNode.pos.getX());
                                    double newY = currentNode.pos.getY() + t * (neighborPos.getY() - currentNode.pos.getY());
                                    double newZ = currentNode.pos.getZ() + t * (neighborPos.getZ() - currentNode.pos.getZ());
                                    BlockPos additionalCubePos = new BlockPos(newX, newY, newZ);
                                    Node additionalCubeNode = new Node(additionalCubePos, currentNode, g, 0);
                                    openList.add(additionalCubeNode);
                                }
                            } else {
                                // Check if this neighbor is within a certain distance of a wall or corner
                                double minDistanceToWallsOrCornersa = Double.MAX_VALUE;
                                for (int i = -maxDistance; i <= maxDistance; i++) {
                                    for (int j = -maxDistance; j <= maxDistance; j++) {
                                        for (int k = -maxDistance; k <= maxDistance; k++) {
                                            BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                            Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                            // Check if the adjacent block is solid
                                            if (adjacentMaterial.isSolid()) {
                                                double distanceToWallOrCorner = Math.sqrt(i * i + j * j + k * k);
                                                minDistanceToWallsOrCornersa = Math.min(minDistanceToWallsOrCornersa, distanceToWallOrCorner);
                                            }
                                        }
                                    }
                                }

                                // Add a penalty to the g value of this neighbor based on its distance to the nearest wall or corner
                                if (minDistanceToWallsOrCornersa < maxDistance) {
                                    g += (maxDistance - minDistanceToWallsOrCornersa) * 5;
                                }
                            }

                            // Check if this neighbor collides with any solid blocks on the sides
                            boolean isCollidingWithSolidBlockOnSides = false;
                            for (int i = -1; i <= 1; i++) {
                                for (int j = -1; j <= 1; j++) {
                                    if (i == 0 && j == 0) continue;

                                    BlockPos adjacentNeighbor = currentNode.pos.add(i, 0, j);
                                    IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                    // Check if the adjacent block is solid and not a slab or stair block
                                    if (adjacentBlockState.getBlock().getMaterial().isSolid() && !(adjacentBlockState.getBlock() instanceof BlockSlab) && !(adjacentBlockState.getBlock() instanceof BlockStairs)) {
                                        isCollidingWithSolidBlockOnSides = true;
                                        break;
                                    }
                                }
                            }

                            if (isCollidingWithSolidBlockOnSides) {
                                // Adjust the path to avoid collision with the solid block on the sides
                                int dx = currentNode.pos.getX() - neighborPos.getX();
                                int dz = currentNode.pos.getZ() - neighborPos.getZ();
                                neighborPos = neighborPos.add(dx * 3, 0, dz * 3);

                                // Recalculate the g value of this neighbor
                                g = currentNode.g + currentNode.pos.distanceSq(neighborPos);
                            } else {
                                // Check if this neighbor is within a certain distance of a wall or corner
                                double minDistanceToWallsOrCornersa = Double.MAX_VALUE;
                                for (int i = -maxDistance; i <= maxDistance; i++) {
                                    for (int j = -maxDistance; j <= maxDistance; j++) {
                                        for (int k = -maxDistance; k <= maxDistance; k++) {
                                            BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                            Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                            // Check if the adjacent block is solid
                                            if (adjacentMaterial.isSolid()) {
                                                double distanceToWallOrCorner = Math.sqrt(i * i + j * j + k * k);
                                                minDistanceToWallsOrCornersa = Math.min(minDistanceToWallsOrCornersa, distanceToWallOrCorner);
                                            }
                                        }
                                    }
                                }

                                // Add a penalty to the g value of this neighbor based on its distance to the nearest wall or corner
                                if (minDistanceToWallsOrCornersa < maxDistance) {
                                    g += (maxDistance - minDistanceToWallsOrCornersa) * 5;
                                }
                            }


                            // Check if this neighbor is adjacent to a wall or block corner and adjust its position to space out the navigation
                            for (int i = -1; i <= 1; i++) {
                                for (int j = 0; j < 3; j++) {
                                    for (int k = -1; k <= 1; k++) {
                                        if (i == 0 && k == 0) continue;

                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                        // Check if the adjacent block is solid
                                        if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                            isAdjacentToWallOrCorner = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isAdjacentToWallOrCorner) {
                                // Add a larger penalty to the g value of this neighbor to space out the navigation
                                g += 5;
                            }

                            // Check if this is part of a turn near a wall or obstacle
                            if (currentNode.parent != null) {
                                BlockPos parentPos = currentNode.parent.pos;
                                if ((parentPos.getX() != currentNode.pos.getX() && currentNode.pos.getZ() != neighborPos.getZ()) ||
                                        (parentPos.getZ() != currentNode.pos.getZ() && currentNode.pos.getX() != neighborPos.getX())) {
                                    // This is part of a turn, so check if it is near a wall or obstacle
                                    boolean isNearWallOrObstacle = false;
                                    for (int i = -1; i <= 1; i++) {
                                        for (int j = 0; j < 3; j++) {
                                            for (int k = -1; k <= 1; k++) {
                                                BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                                IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                                // Check if the adjacent block is solid
                                                if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                                    isNearWallOrObstacle = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    if (isNearWallOrObstacle) {
                                        // This turn is near a wall or obstacle, so add a penalty to the g value of this neighbor
                                        g += 7;
                                    }
                                }
                            }

                            // Check if this neighbor is adjacent to a wall on either side
                            boolean isAdjacentToWallOnEitherSide = false;
                            for (int i = -1; i <= 1; i += 2) {
                                for (int j = 0; j < 3; j++) {
                                    BlockPos adjacentNeighbor = currentNode.pos.add(i, j, 0);
                                    IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                    // Check if the adjacent block is solid
                                    if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                        isAdjacentToWallOnEitherSide = true;
                                        break;
                                    }
                                }
                            }

                            if (isAdjacentToWallOnEitherSide) {
                                // Add a penalty to the g value of this neighbor to encourage staying in the middle of the trail
                                g += 4;
                            }

                            // Check if this is part of a sharp turn
                            if (currentNode.parent != null) {
                                BlockPos parentPos = currentNode.parent.pos;
                                if ((parentPos.getX() == currentNode.pos.getX() && currentNode.pos.getZ() == neighborPos.getZ()) ||
                                        (parentPos.getZ() == currentNode.pos.getZ() && currentNode.pos.getX() == neighborPos.getX())) {
                                    // This is part of a sharp turn, so add a penalty to the g value of this neighbor
                                    g += 7;
                                }
                            }
                            boolean isCollidingWithSolidBlockb = false;
                            for (int j = 0; j < 4; j++) {
                                BlockPos adjacentNeighbor = currentNode.pos.add(0, j, 0);
                                IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                // Check if the adjacent block is solid
                                if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                    isCollidingWithSolidBlockb = true;
                                    break;
                                }
                            }

                            if (isCollidingWithSolidBlockb) {
                                // Add additional cube positions around the turn to space out the turn and avoid collision
                                int numAdditionalCubes = 3;
                                for (int i = 1; i <= numAdditionalCubes; i++) {
                                    double t = (double) i / (numAdditionalCubes + 1);
                                    double newX = currentNode.pos.getX() + t * (neighborPos.getX() - currentNode.pos.getX());
                                    double newY = currentNode.pos.getY() + t * (neighborPos.getY() - currentNode.pos.getY());
                                    double newZ = currentNode.pos.getZ() + t * (neighborPos.getZ() - currentNode.pos.getZ());
                                    BlockPos additionalCubePos = new BlockPos(newX, newY, newZ);
                                    Node additionalCubeNode = new Node(additionalCubePos, currentNode, g, 0);
                                    openList.add(additionalCubeNode);
                                }
                            } else {
                                // Check if this neighbor is within a certain distance of a wall or corner
                                int maxDistancea = 3;
                                double minDistanceToWallsOrCornersa = Double.MAX_VALUE;
                                for (int i = -maxDistancea; i <= maxDistancea; i++) {
                                    for (int j = -maxDistancea; j <= maxDistancea; j++) {
                                        for (int k = -maxDistancea; k <= maxDistancea; k++) {
                                            BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                            Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                            // Check if the adjacent block is solid
                                            if (adjacentMaterial.isSolid()) {
                                                double distanceToWallOrCorner = Math.sqrt(i * i + j * j + k * k);
                                                minDistanceToWallsOrCornersa = Math.min(minDistanceToWallsOrCornersa, distanceToWallOrCorner);
                                            }
                                        }
                                    }
                                }
                            }


                            // Check if this neighbor is within 2 blocks of a wall
                            boolean isWithin2BlocksOfWall = false;
                            for (int i = -2; i <= 2; i++) {
                                for (int j = 0; j < 3; j++) {
                                    for (int k = -2; k <= 2; k++) {
                                        if (i == 0 && k == 0) continue;

                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                        // Check if the adjacent block is solid
                                        if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                            isWithin2BlocksOfWall = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isWithin2BlocksOfWall) {
                                // Add a larger penalty to the g value of this neighbor to space out the navigation
                                g += 5;
                            }

                            // Check if this neighbor collides with any solid blocks
                            boolean isCollidingWithSolidBlocka = false;
                            for (int j = 0; j < 4; j++) {
                                BlockPos adjacentNeighbor = currentNode.pos.add(0, j, 0);
                                IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                // Check if the adjacent block is solid
                                if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                    isCollidingWithSolidBlocka = true;
                                    break;
                                }
                            }

                            if (isCollidingWithSolidBlocka) {
                                // Move 1-2 blocks counter opposite to the direction of the block
                                int dx = currentNode.pos.getX() - neighborPos.getX();
                                int dz = currentNode.pos.getZ() - neighborPos.getZ();
                                neighborPos = neighborPos.add(dx * 3, 0, dz * 3);

                                // Recalculate the g value of this neighbor
                                g = currentNode.g + currentNode.pos.distanceSq(neighborPos) + 1;
                            }

                            for (int i = -1; i <= 1; i += 2) {
                                for (int j = 0; j < 3; j++) {
                                    BlockPos adjacentNeighbor = currentNode.pos.add(i, j, 0);
                                    IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                    // Check if the adjacent block is solid
                                    if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                        isAdjacentToLeftOrRight = true;
                                        break;
                                    }
                                }
                            }

                            // In the findPathDijkstra method, add the following code after checking if the neighbor block is solid or a stair, slab or carpet
                            // Check if this neighbor is within 4 blocks of a wall on either side


                            boolean isWithin4BlocksOfWallOnEitherSide = false;
                            for (int i = -4; i <= 4; i += 8) {
                                for (int j = 0; j < 4; j++) {
                                    BlockPos adjacentNeighbor = currentNode.pos.add(i, j, 0);
                                    IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                    // Check if the adjacent block is solid
                                    if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                        isWithin4BlocksOfWallOnEitherSide = true;
                                        break;
                                    }
                                }
                            }

                            if (isWithin4BlocksOfWallOnEitherSide) {
                                // Add a penalty to the g value of this neighbor to space out the navigation
                                g += 9;
                            }

                            // Check if this neighbor is within 2 blocks of a 1-block tall wall on either side
                            boolean isWithin3BlocksOf1BlockTallWallOnEitherSide = false;
                            for (int i = -3; i <= 3; i += 6) {
                                BlockPos adjacentNeighbor = currentNode.pos.add(i, 0, 0);
                                IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                // Check if the adjacent block is solid and the block above it is not solid
                                if (adjacentBlockState.getBlock().getMaterial().isSolid() && !world.getBlockState(adjacentNeighbor.up()).getBlock().getMaterial().isSolid()) {
                                    isWithin3BlocksOf1BlockTallWallOnEitherSide = true;
                                    break;
                                }
                            }

                            if (isWithin3BlocksOf1BlockTallWallOnEitherSide) {
                                // Add a penalty to the g value of this neighbor to space out the navigation
                                g += 8;
                            }

                            // Calculate the distance from this neighbor to the nearest wall or obstacle

                            // Check if this neighbor is adjacent to a corner
                            boolean isAdjacentToCorner = false;
                            for (int i = -1; i <= 1; i += 2) {
                                for (int j = 0; j < 3; j++) {
                                    for (int k = -1; k <= 1; k += 2) {
                                        BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                        IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                        // Check if the adjacent block is solid
                                        if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                            isAdjacentToCorner = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (isAdjacentToCorner) {
                                // Add a penalty to the g value of this neighbor to space out the navigation
                                g += 2;
                            }

                            // Check if this is part of a sharp turn
                            if (currentNode.parent != null) {
                                BlockPos parentPos = currentNode.parent.pos;
                                if ((parentPos.getX() != currentNode.pos.getX() && currentNode.pos.getZ() != neighborPos.getZ()) ||
                                        (parentPos.getZ() != currentNode.pos.getZ() && currentNode.pos.getX() != neighborPos.getX())) {
                                    // This is part of a sharp turn, so add a penalty to the g value of this neighbor
                                    g += 7;
                                }
                            }

                            // Check if this is part of a 90-degree turn
                            if (currentNode.parent != null) {
                                BlockPos parentPos = currentNode.parent.pos;
                                if ((parentPos.getX() == currentNode.pos.getX() && currentNode.pos.getZ() == neighborPos.getZ()) ||
                                        (parentPos.getZ() == currentNode.pos.getZ() && currentNode.pos.getX() == neighborPos.getX()) ||
                                        (parentPos.getY() != currentNode.pos.getY() && currentNode.pos.getX() != neighborPos.getX() && currentNode.pos.getZ() != neighborPos.getZ())) {
                                    // This is part of a 90-degree turn, so add a penalty to the g value of this neighbor
                                    g += 6;
                                }
                            }

                            // Check if this is part of a turn while going up or down stairs
                            if (currentNode.parent != null) {
                                BlockPos parentPos = currentNode.parent.pos;
                                if (parentPos.getY() != currentNode.pos.getY() && currentNode.pos.getX() != neighborPos.getX() && currentNode.pos.getZ() != neighborPos.getZ()) {
                                    // This is part of a turn while going up or down stairs, so add a penalty to the g value of this neighbor
                                    g += 8;
                                }
                            }


                            // Add a penalty to the g value of this neighbor based on its distance to the nearest wall or obstacle
                            g += 2 / minDistanceToWall;

                            if (isAdjacentToLeftOrRight) {
                                // Add a penalty to the g value of this neighbor to space out the navigation
                                g += 2;
                            }

                            if (isAdjacentToWall) {
                                // Add a penalty to the g value of this neighbor to space out the navigation
                                g += 2;
                            }

                            if (isAdjacentToWallOrCorner) {
                                // Adjust the position of this neighbor to space out the navigation
                                neighborPos = neighborPos.add(x * 0.5, 0, z * 0.5);
                            }


                            if (isBlocked) continue;
                        }

                        // Check if this neighbor is within 1-2 blocks of a liquid
                        boolean isWithin2BlocksOfLiquid = false;
                        for (int i = -2; i <= 2; i++) {
                            for (int j = -2; j <= 2; j++) {
                                for (int k = -2; k <= 2; k++) {
                                    BlockPos adjacentNeighbor = currentNode.pos.add(i, j, k);
                                    Material adjacentMaterial = world.getBlockState(adjacentNeighbor).getBlock().getMaterial();

                                    // Check if the adjacent block is a liquid
                                    if (adjacentMaterial.isLiquid()) {
                                        isWithin2BlocksOfLiquid = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (isWithin2BlocksOfLiquid) {
                            // Add a penalty to the g value of this neighbor to space out the navigation
                            g += 6;
                        }

                        // Check if this neighbor is in the middle of a trail
                        boolean isInMiddleOfTrail = true;
                        for (int i = -1; i <= 1; i += 2) {
                            for (int j = 0; j < 3; j++) {
                                BlockPos adjacentNeighbor = currentNode.pos.add(i, j, 0);
                                IBlockState adjacentBlockState = world.getBlockState(adjacentNeighbor);

                                // Check if the adjacent block is solid
                                if (adjacentBlockState.getBlock().getMaterial().isSolid()) {
                                    isInMiddleOfTrail = false;
                                    break;
                                }
                            }
                        }

                        if (!isInMiddleOfTrail) {
                            // Add a penalty to the g value of this neighbor to encourage staying in the middle of the trail
                            g += 5.76;
                        }


                        // Check if the neighbor is already in the closed list
                        boolean inClosedList = closedList.contains(neighborPos);
                        if (inClosedList) continue;

                        // Check if the neighbor is already in the open list
                        boolean inOpenList = false;
                        for (Node node : openList) {
                            if (node.pos.equals(neighborPos)) {
                                inOpenList = true;

                                // Update the g value of the neighbor if a shorter path has been found
                                if (g < node.g) {
                                    node.parent = currentNode;
                                    node.g = g;
                                    node.f = node.g;
                                }

                                break;
                            }
                        }


                        // Add the neighbor to the open list
                        if (!inOpenList) {
                            Node neighborNode = new Node(neighborPos, currentNode, g, 0);
                            openList.add(neighborNode);
                        }

                        pathDijkstra = addAdditionalCubePositions(pathDijkstra, 5);

                    }
                }
            }
        }

        // No path was found
        return null;
    }

    private void checkForLineDeviation(World world, EntityPlayer player) {
        if (isFollowingPath && cubePositions != null && !cubePositions.isEmpty()) {
            // Get the next position in the path
            BlockPos nextPos = cubePositions.get(currentPathIndex);

            // Calculate the distance between the player and the next position in the path

            double distance = 0;
            if (nextPos != null) {
                double diffX = nextPos.getX() + 0.5 - player.posX;
                double diffZ = nextPos.getZ() + 0.5 - player.posZ;
                distance = Math.sqrt(diffX * diffX + diffZ * diffZ);
                // rest of your code
            } else {
                // handle the case where nextPos is null
                player.addChatMessage(new ChatComponentText("nextPos was null"));
            }

            // Check if the player is deviating from the path
            if (distance > 5) {
                // The player is deviating from the path, so end the current pathfinding process and start a new one from the player's position
                startPosDijkstra = new BlockPos(player.posX, player.posY - 1, player.posZ);
                currentPathIndex = 0;

                if (cubePositions != null) {
                    cubePositions.clear();
                }

                player.addChatMessage(new ChatComponentText(": " + "Path cleared..."));
                player.addChatMessage(new ChatComponentText("Following State: [" + isFollowingPath + "]"));

                // Pause the player's movement
                shouldPauseMovement = true;

                // Add a delay before starting the new pathfinding process
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Resume the player's movement
                shouldPauseMovement = false;
            }
        }
    }

    private List<BlockPos> addAdditionalCubePositions(List<BlockPos> path, double maxDistance) {
        if (path == null || path.size() < 2) {
            // The path is too short to add additional cube positions, so return it unchanged
            return path;
        }

        List<BlockPos> newPath = new ArrayList<BlockPos>();

        // Add the first position in the path to the new path
        newPath.add(path.get(0));

        // Loop through the positions in the path and add additional cube positions if necessary
        for (int i = 1; i < path.size(); i++) {
            BlockPos prevPos = path.get(i - 1);
            BlockPos currentPos = path.get(i);

            // Calculate the distance between the previous position and the current position
            double distance = getDistance(prevPos, currentPos);

            // Check if the distance between the previous position and the current position is greater than the maximum allowed distance
            if (distance > maxDistance) {
                // The distance is too great, so add additional cube positions between the previous position and the current position
                int numAdditionalCubes = (int) Math.ceil(distance / maxDistance) - 1;
                for (int j = 1; j <= numAdditionalCubes; j++) {
                    double t = (double) j / (numAdditionalCubes + 1);
                    double x = prevPos.getX() + t * (currentPos.getX() - prevPos.getX());
                    double y = prevPos.getY() + t * (currentPos.getY() - prevPos.getY());
                    double z = prevPos.getZ() + t * (currentPos.getZ() - prevPos.getZ());
                    BlockPos additionalCubePos = new BlockPos(x, y, z);
                    newPath.add(additionalCubePos);
                }
            }

            // Add the current position to the new path
            newPath.add(currentPos);
        }

        return newPath;
    }

    private MovingObjectPosition performRayTrace(Vec3 start, Vec3 direction, World world) {
        // Calculate the end point of the raytrace
        Vec3 endPos = start.addVector(direction.xCoord * 0.5, direction.yCoord * 0.5, direction.zCoord * 0.5);

        // Perform the raytrace
        return world.rayTraceBlocks(start, endPos, false, false, true);
    }

    private void checkRayTraceResult(MovingObjectPosition rayTraceResult, World world, EntityPlayer player) {
        if (rayTraceResult != null && rayTraceResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            // The raytrace hit a block
            BlockPos blockPos = rayTraceResult.getBlockPos();
            IBlockState blockState = world.getBlockState(blockPos);


            // Check if the block is solid
            if (blockState.getBlock().getMaterial().isSolid() && !(blockState.getBlock().isPassable(world, blockPos))) {
                // The player is colliding with a solid block, so start the algorithm from a random solid block near the player's position
                BlockPos startPos = findRandomSolidBlockNearPlayer(world, player);
                if (startPos != null) {
                    startPosDijkstra = player.getPosition();
                    tempEndPos = endPosDijkstra;
                    endPosDijkstra = startPos;
                    currentPathIndex = 0;
                    isInRayTracingPath = true;

                    if (cubePositions != null) {
                        cubePositions.clear();
                    }

                    player.addChatMessage(new ChatComponentText(": " + "Path cleared..."));
                    player.addChatMessage(new ChatComponentText("Following State: [" + isFollowingPath + "]"));

                    // Pause the player's movement
                    shouldPauseMovement = true;

                    // Add a delay before starting the new pathfinding process
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Resume the player's movement
                    shouldPauseMovement = false;
                }
            }
        }
    }

    private BlockPos findRandomSolidBlockNearPlayer(World world, EntityPlayer player) {
        Random rand = new Random();
        for (int i = 0; i < 100; i++) { // Try up to 100 times
            int dx = rand.nextInt(5) - 2; // Random number between -2 and 2
            int dz = rand.nextInt(5) - 2; // Random number between -2 and 2
            BlockPos pos = new BlockPos(player.posX + dx, player.posY, player.posZ + dz);
            if (world.getBlockState(pos).getBlock().getMaterial().isSolid() && // The block is solid
                    world.isAirBlock(pos.up()) && world.isAirBlock(pos.up(2))) { // There are two air blocks above it
                return pos;
            }
        }
        return null; // No suitable block was found
    }


}