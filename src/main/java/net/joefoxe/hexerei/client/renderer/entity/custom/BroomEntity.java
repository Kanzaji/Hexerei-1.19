package net.joefoxe.hexerei.client.renderer.entity.custom;

import net.joefoxe.hexerei.client.renderer.entity.ModEntityTypes;
import net.joefoxe.hexerei.item.ModItems;
import net.joefoxe.hexerei.particle.ModParticleTypes;
import net.joefoxe.hexerei.util.IPacket;
import net.minecraft.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class BroomEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIME_SINCE_HIT = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORWARD_DIRECTION = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE_TAKEN = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LEFT_PADDLE = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RIGHT_PADDLE = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ROCKING_TICKS = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    public float speedMultiplier = 0.75f;
//    protected boolean charging;
    private final float[] paddlePositions = new float[2];
    private float momentum;
    private float outOfControlTicks;
    public float deltaRotation;
    public float floatingOffset;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private boolean leftInputDown;
    private boolean rightInputDown;
    private boolean forwardInputDown;
    private boolean backInputDown;
    private boolean jumpInputDown;
    private boolean sneakingInputDown;
    private double waterLevel;
    private float boatGlide;
    private BroomEntity.Status status;
    private BroomEntity.Status previousStatus;
    private double lastYd;
    private boolean rocking;
    private boolean downwards;
    private float rockingIntensity;
    private float rockingAngle;
    private float prevRockingAngle;

    public BroomEntity(Level worldIn, double x, double y, double z) {
        this(ModEntityTypes.BROOM.get(), worldIn);
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public BroomEntity(EntityType<BroomEntity> broomEntityEntityType, Level world) {
        super(broomEntityEntityType, world);
//        this.preventEntitySpawning = true;
    }

    @Override
    protected float getEyeHeight(Pose poseIn, EntityDimensions sizeIn) {
        return sizeIn.height;
    }

//    @Override
//    protected boolean canTriggerWalking() {
//        return false;
//    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TIME_SINCE_HIT, 0);
        this.entityData.define(FORWARD_DIRECTION, 1);
        this.entityData.define(DAMAGE_TAKEN, 0.0F);
        this.entityData.define(LEFT_PADDLE, false);
        this.entityData.define(RIGHT_PADDLE, false);
        this.entityData.define(ROCKING_TICKS, 0);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return func_242378_a(this, entity);
    }

    public static boolean func_242378_a(Entity p_242378_0_, Entity entity) {
        return (entity.canBeCollidedWith() || entity.isPushable()) && !p_242378_0_.isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /**
     * Returns true if this entity should pushPose and be pushed by other entities when colliding.
     */
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle result) {
        return PortalShape.getRelativePosition(result, axis, this.position(), this.getDimensions(this.getPose()));
    }

    /**
     * Returns the Y offset from the entity's position for any entity riding this one.
     */
    @Override
    public double getPassengersRidingOffset() {
        return floatingOffset - 1.8f;
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (!this.level.isClientSide && !this.isRemoved()) {
            this.setForwardDirection(-this.getForwardDirection());
            this.setTimeSinceHit(10);
            this.setDamageTaken(this.getDamageTaken() + amount * 10.0F);
            this.markHurt();
            boolean flag = source.getDirectEntity() instanceof Player && ((Player)source.getDirectEntity()).getAbilities().instabuild;
            if (flag || this.getDamageTaken() > 40.0F) {
                if (!flag && this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    this.spawnAtLocation(this.getItemBoat());
                }

                this.remove(RemovalReason.DISCARDED);
            }

            return true;
        } else {
            return true;
        }
    }

    @Override
    public void onAboveBubbleCol(boolean downwards) {
        if (!this.level.isClientSide) {
            this.rocking = true;
            this.downwards = downwards;
            if (this.getRockingTicks() == 0) {
                this.setRockingTicks(60);
            }
        }

        this.level.addParticle(ParticleTypes.SPLASH, this.getX() + (double)this.random.nextFloat(), this.getY() + 0.7D, this.getZ() + (double)this.random.nextFloat(), 0.0D, 0.0D, 0.0D);
        if (this.random.nextInt(20) == 0) {
            this.level.playSound(null, this.getX(), this.getY(), this.getZ(), this.getSwimSplashSound(), this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat());
        }

    }

    /**
     * Applies a velocity to the entities, to push them away from eachother.
     */
    @Override
    public void push(Entity entityIn) {
        if (entityIn instanceof BroomEntity) {
            if (entityIn.getBoundingBox().minY < this.getBoundingBox().maxY) {
                super.push(entityIn);
            }
        } else if (entityIn.getBoundingBox().minY <= this.getBoundingBox().minY) {
            super.push(entityIn);
        }

    }

    public Item getItemBoat() {
        return ModItems.BROOM.get();
    }

    /**
     * Setups the entity to do the hurt animation. Only used by packets in multiplayer.
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateHurt() {
        this.setForwardDirection(-this.getForwardDirection());
        this.setTimeSinceHit(10);
        this.setDamageTaken(this.getDamageTaken() * 11.0F);
    }

    /**
     * Returns true if other Entities should be prevented from moving through this Entity.
     */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /**
     * Sets a target for the client to interpolate towards over the next few ticks
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = (double)yaw;
        this.lerpPitch = (double)pitch;
        this.lerpSteps = 10;
    }

    /**
     * Gets the horizontal facing direction of this Entity, adjusted to take specially-treated entity types into account.
     */
    @Override
    public Direction getMotionDirection() {
        return this.getDirection().getClockWise();
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        this.previousStatus = this.status;
        this.status = this.getBoatStatus();
        if (this.status != BroomEntity.Status.UNDER_WATER && this.status != BroomEntity.Status.UNDER_FLOWING_WATER) {
            this.outOfControlTicks = 0.0F;
        } else {
            ++this.outOfControlTicks;
        }

        if (!this.level.isClientSide && this.outOfControlTicks >= 60.0F) {
            this.ejectPassengers();
        }

        if (this.getTimeSinceHit() > 0) {
            this.setTimeSinceHit(this.getTimeSinceHit() - 1);
        }

        if (this.getDamageTaken() > 0.0F) {
            this.setDamageTaken(this.getDamageTaken() - 1.0F);
        }

        Entity entityPassenger = this.getControllingPassenger();
        if(this.level.isClientSide()) {
            if (entityPassenger instanceof LivingEntity && entityPassenger.equals(Minecraft.getInstance().player)) {
                LocalPlayer player = Minecraft.getInstance().player;
                this.setNoGravity(true);
                updateInputs(player.input.left, player.input.right, player.input.up, player.input.down, player.input.jumping, player.input.shiftKeyDown);
            } else if (entityPassenger == null) {
                this.setNoGravity(false);
            }
        }

//        this.setBoundingBox(AABB); TODO make standing broom

        super.tick();
        this.tickLerp();
        if (this.isControlledByLocalInstance()) {
            if (this.getPassengers().isEmpty() || !(this.getPassengers().get(0) instanceof Player)) {
                this.setPaddleState(false, false);
            }

            floatingOffset = moveTo(floatingOffset, 0.05f + (float)Math.sin(level.getGameTime()/30f) * 0.15f, 0.02f);


            this.updateMotion();
            if (this.level.isClientSide) {
                this.controlBoat();
                this.level.sendPacketToServer(new ServerboundPaddleBoatPacket(this.getPaddleState(0), this.getPaddleState(1)));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());


            Random random = new Random();
            if(random.nextInt(50)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(20)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_2.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(80)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_3.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(500)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_4.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(500)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_5.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(500)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_6.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }


        } else {
            this.setDeltaMovement(Vec3.ZERO);
            floatingOffset = moveTo(floatingOffset, 0, 0.02f);
        }

        this.updateRocking();

        for(int i = 0; i <= 1; ++i) {
            if (this.getPaddleState(i)) {
                if (!this.isSilent() && (double)(this.paddlePositions[i] % ((float)Math.PI * 2F)) <= (double)((float)Math.PI / 4F) && ((double)this.paddlePositions[i] + (double)((float)Math.PI / 8F)) % (double)((float)Math.PI * 2F) >= (double)((float)Math.PI / 4F)) {
                    SoundEvent soundevent = this.getPaddleSound();
                    if (soundevent != null) {
                        Vec3 vector3d = this.getViewVector(1.0F);
                        double d0 = i == 1 ? -vector3d.z : vector3d.z;
                        double d1 = i == 1 ? vector3d.x : -vector3d.x;
                        this.level.playSound((Player)null, this.getX() + d0, this.getY(), this.getZ() + d1, soundevent, this.getSoundSource(), 1.0F, 0.8F + 0.4F * this.random.nextFloat());
                    }
                }

                this.paddlePositions[i] = (float)((double)this.paddlePositions[i] + (double)((float)Math.PI / 8F));
            } else {
                this.paddlePositions[i] = 0.0F;
            }
        }

        this.checkInsideBlocks();
        List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate((double)0.2F, (double)-0.01F, (double)0.2F), EntitySelector.pushableBy(this));
        if (!list.isEmpty()) {
            boolean flag = !this.level.isClientSide && !(this.getControllingPassenger() instanceof Player);

            for(int j = 0; j < list.size(); ++j) {
                Entity entity = list.get(j);
                if (!entity.hasPassenger(this)) {
                    if (flag && this.getPassengers().size() < 1 && !entity.isPassenger() && entity.getBbWidth() < this.getBbWidth() && entity instanceof LivingEntity && !(entity instanceof WaterAnimal) && !(entity instanceof Player)) {
                        //entity.startRiding(this);
                    } else {
                        this.push(entity);
                    }
                }
            }
        }


        if(level.isClientSide() && this.getDeltaMovement().length() >= 0.01d) {
            Random random = new Random();
            if(random.nextInt(5)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(2)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_2.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(8)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_3.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(50)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_4.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(50)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_5.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
            if(random.nextInt(50)==0) {
                float rotOffset = random.nextFloat() * 10 - 5;
                level.addParticle(ModParticleTypes.BROOM_6.get(), getX() - Math.sin(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), getY() + floatingOffset + 0.2f * random.nextFloat() - this.getDeltaMovement().y(), getZ() + Math.cos(((this.getYRot() - 90f + deltaRotation + rotOffset) / 180f) * (Math.PI)) * (1.25f + this.getDeltaMovement().length()/4), (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d, (random.nextDouble() - 0.5d) * 0.015d);
            }
        }

    }

    private float moveTo(float input, float moveTo, float speed)
    {
        float distance = moveTo - input;

        if(Math.abs(distance) <= speed)
        {
            return moveTo;
        }

        if(distance > 0)
        {
            input += speed;
        } else {
            input -= speed;
        }

        return input;
    }


    @OnlyIn(Dist.CLIENT)
    public void onClientUpdate()
    {
        Entity entity = this.getControllingPassenger();
        if(entity instanceof LivingEntity && entity.equals(Minecraft.getInstance().player))
        {
            LivingEntity livingEntity = (LivingEntity) entity;

            AccelerationDirection acceleration = getAccelerationDirection(livingEntity);
//            if(this.getAcceleration() != acceleration)
//            {
//                this.setAcceleration(acceleration);
//                HexereiPacketHandler.instance.sendToServer(new MessageAccelerating(acceleration));
//            }

//            TurnDirection direction = getTurnDirection(livingEntity);
//            if(this.getTurnDirection() != direction)
//            {
//                this.setTurnDirection(direction);
//                PacketHandler.instance.sendToServer(new MessageTurnDirection(direction));
//            }
//
//            float targetTurnAngle = getTargetTurnAngle(this, false);
//            this.setTargetTurnAngle(targetTurnAngle);
//            PacketHandler.instance.sendToServer(new MessageTurnAngle(targetTurnAngle));
        }

    }


    public static AccelerationDirection getAccelerationDirection(LivingEntity entity)
    {
        Options settings = Minecraft.getInstance().options;
        boolean forward = settings.keyUp.isDown();
        boolean reverse = settings.keyDown.isDown();
        if(forward && reverse)
        {
            return BroomEntity.AccelerationDirection.CHARGING;
        }
        else if(forward)
        {
            return BroomEntity.AccelerationDirection.FORWARD;
        }
        else if(reverse)
        {
            return BroomEntity.AccelerationDirection.REVERSE;
        }

        return BroomEntity.AccelerationDirection.fromEntity(entity);
    }

    private void updateRocking() {
        if (this.level.isClientSide) {
            int i = this.getRockingTicks();
            if (i > 0) {
                this.rockingIntensity += 0.05F;
            } else {
                this.rockingIntensity -= 0.1F;
            }

            this.rockingIntensity = Mth.clamp(this.rockingIntensity, 0.0F, 1.0F);
            this.prevRockingAngle = this.rockingAngle;
            this.rockingAngle = 10.0F * (float)Math.sin((double)(0.5F * (float)this.level.getGameTime())) * this.rockingIntensity;
        } else {
            if (!this.rocking) {
                this.setRockingTicks(0);
            }

            int k = this.getRockingTicks();
            if (k > 0) {
                --k;
                this.setRockingTicks(k);
                int j = 60 - k - 1;
                if (j > 0 && k == 0) {
                    this.setRockingTicks(0);
                    Vec3 vector3d = this.getDeltaMovement();
                    if (this.downwards) {
                        this.setDeltaMovement(vector3d.add(0.0D, -0.7D, 0.0D));
                        this.ejectPassengers();
                    } else {
                        this.setDeltaMovement(vector3d.x, this.hasPassenger((p_150274_) -> {
                            return p_150274_ instanceof Player;
                        }) ? 2.7D : 0.6D, vector3d.z);
                    }
                }

                this.rocking = false;
            }
        }

    }

    @Nullable
    protected SoundEvent getPaddleSound() {
        switch(this.getBoatStatus()) {
            case IN_WATER:
            case UNDER_WATER:
            case UNDER_FLOWING_WATER:
                return SoundEvents.BOAT_PADDLE_WATER;
            case ON_LAND:
                return SoundEvents.BOAT_PADDLE_LAND;
            case IN_AIR:
            default:
                return null;
        }
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.setPacketCoordinates(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            double d0 = this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps;
            double d1 = this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps;
            double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps;
            double d3 = Mth.wrapDegrees(this.getYRot() - (double)this.getYRot());
            this.setYRot((float)((double)this.getYRot() + d3 / (double)this.lerpSteps));
            this.setXRot((float)((double)this.getXRot() + (this.lerpPitch - (double)this.getXRot()) / (double)this.lerpSteps));
            --this.lerpSteps;
            this.setPos(d0, d1, d2);
            this.setRot((float)this.getYRot(), this.getXRot());
        }
    }


    public void setPaddleState(boolean left, boolean right) {
        this.entityData.set(LEFT_PADDLE, left);
        this.entityData.set(RIGHT_PADDLE, right);
    }

    @OnlyIn(Dist.CLIENT)
    public float getRowingTime(int side, float limbSwing) {
        return this.getPaddleState(side) ? (float)Mth.clampedLerp((double)this.paddlePositions[side] - (double)((float)Math.PI / 8F), (double)this.paddlePositions[side], (double)limbSwing) : 0.0F;
    }

    /**
     * Determines whether the boat is in water, gliding on land, or in air
     */
    private BroomEntity.Status getBoatStatus() {
        BroomEntity.Status boatentity$status = this.getUnderwaterStatus();
        if (boatentity$status != null) {
            this.waterLevel = this.getBoundingBox().maxY;
            return boatentity$status;
        } else if (this.checkInWater()) {
            return BroomEntity.Status.IN_WATER;
        } else {
            float f = this.getBoatGlide();
            if (f > 0.0F) {
                this.boatGlide = f;
                return BroomEntity.Status.ON_LAND;
            } else {
                return BroomEntity.Status.IN_AIR;
            }
        }
    }

    public float getWaterLevelAbove() {
        AABB axisalignedbb = this.getBoundingBox();
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.maxY);
        int l = Mth.ceil(axisalignedbb.maxY - this.lastYd);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

        label39:
        for(int k1 = k; k1 < l; ++k1) {
            float f = 0.0F;

            for(int l1 = i; l1 < j; ++l1) {
                for(int i2 = i1; i2 < j1; ++i2) {
                    blockpos$mutable.set(l1, k1, i2);
                    FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
                    if (fluidstate.is(FluidTags.WATER)) {
                        f = Math.max(f, fluidstate.getHeight(this.level, blockpos$mutable));
                    }

                    if (f >= 1.0F) {
                        continue label39;
                    }
                }
            }

            if (f < 1.0F) {
                return (float)blockpos$mutable.getY() + f;
            }
        }

        return (float)(l + 1);
    }

    /**
     * Decides how much the boat should be gliding on the land (based on any slippery blocks)
     */
    public float getBoatGlide() {
        AABB axisalignedbb = this.getBoundingBox();
        AABB axisalignedbb1 = new AABB(axisalignedbb.minX, axisalignedbb.minY - 0.001D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
        int i = Mth.floor(axisalignedbb1.minX) - 1;
        int j = Mth.ceil(axisalignedbb1.maxX) + 1;
        int k = Mth.floor(axisalignedbb1.minY) - 1;
        int l = Mth.ceil(axisalignedbb1.maxY) + 1;
        int i1 = Mth.floor(axisalignedbb1.minZ) - 1;
        int j1 = Mth.ceil(axisalignedbb1.maxZ) + 1;
        VoxelShape voxelshape = Shapes.create(axisalignedbb1);
        float f = 0.0F;
        int k1 = 0;
        BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

        for(int l1 = i; l1 < j; ++l1) {
            for(int i2 = i1; i2 < j1; ++i2) {
                int j2 = (l1 != i && l1 != j - 1 ? 0 : 1) + (i2 != i1 && i2 != j1 - 1 ? 0 : 1);
                if (j2 != 2) {
                    for(int k2 = k; k2 < l; ++k2) {
                        if (j2 <= 0 || k2 != k && k2 != l - 1) {
                            blockpos$mutable.set(l1, k2, i2);
                            BlockState blockstate = this.level.getBlockState(blockpos$mutable);
                            if (!(blockstate.getBlock() instanceof WaterlilyBlock) && Shapes.joinIsNotEmpty(blockstate.getCollisionShape(this.level, blockpos$mutable).move((double)l1, (double)k2, (double)i2), voxelshape, BooleanOp.AND)) {
                                f += blockstate.getFriction(this.level, blockpos$mutable, this);
                                ++k1;
                            }
                        }
                    }
                }
            }
        }

        return f / (float)k1;
    }

    private boolean checkInWater() {
        AABB axisalignedbb = this.getBoundingBox();
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minY);
        int l = Mth.ceil(axisalignedbb.minY + 0.001D);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        boolean flag = false;
        this.waterLevel = Double.MIN_VALUE;
        BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

        for(int k1 = i; k1 < j; ++k1) {
            for(int l1 = k; l1 < l; ++l1) {
                for(int i2 = i1; i2 < j1; ++i2) {
                    blockpos$mutable.set(k1, l1, i2);
                    FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
                    if (fluidstate.is(FluidTags.WATER)) {
                        float f = (float)l1 + fluidstate.getHeight(this.level, blockpos$mutable);
                        this.waterLevel = Math.max((double)f, this.waterLevel);
                        flag |= axisalignedbb.minY < (double)f;
                    }
                }
            }
        }

        return flag;
    }

    /**
     * Decides whether the boat is currently underwater.
     */
    @Nullable
    private BroomEntity.Status getUnderwaterStatus() {
        AABB axisalignedbb = this.getBoundingBox();
        double d0 = axisalignedbb.maxY + 0.001D;
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.maxY);
        int l = Mth.ceil(d0);
        int i1 = Mth.floor(axisalignedbb.minZ);
        int j1 = Mth.ceil(axisalignedbb.maxZ);
        boolean flag = false;
        BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

        for(int k1 = i; k1 < j; ++k1) {
            for(int l1 = k; l1 < l; ++l1) {
                for(int i2 = i1; i2 < j1; ++i2) {
                    blockpos$mutable.set(k1, l1, i2);
                    FluidState fluidstate = this.level.getFluidState(blockpos$mutable);
                    if (fluidstate.is(FluidTags.WATER) && d0 < (double)((float)blockpos$mutable.getY() + fluidstate.getHeight(this.level, blockpos$mutable))) {
                        if (!fluidstate.isSource()) {
                            return BroomEntity.Status.UNDER_FLOWING_WATER;
                        }

                        flag = true;
                    }
                }
            }
        }

        return flag ? BroomEntity.Status.UNDER_WATER : null;
    }

    /**
     * Update the boat's speed, based on momentum.
     */
    private void updateMotion() {
        double d0 = (double)-0.04F;
        double d1 = this.isNoGravity() ? 0.0D : (double)-0.04F;
        double d2 = 0.0D;
        this.momentum = 0.05F;
        if (this.previousStatus == BroomEntity.Status.IN_AIR && this.status != BroomEntity.Status.IN_AIR && this.status != BroomEntity.Status.ON_LAND) {
            this.waterLevel = this.getY(1.0D);
            this.setPos(this.getX(), (double)(this.getWaterLevelAbove() - this.getEyeY()) + 0.101D, this.getZ());
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            this.lastYd = 0.0D;
            this.status = BroomEntity.Status.IN_WATER;
        } else {
            if (this.status == BroomEntity.Status.IN_WATER) {
                d2 = (this.waterLevel - this.getY()) / (double)this.getBbHeight();
                this.momentum = 0.9F;
            } else if (this.status == BroomEntity.Status.UNDER_FLOWING_WATER) {
//                d1 = -7.0E-4D;
                this.momentum = 0.9F;
            } else if (this.status == BroomEntity.Status.UNDER_WATER) {
                d2 = (double)0.01F;
                this.momentum = 0.45F;
            } else if (this.status == BroomEntity.Status.IN_AIR) {
                this.momentum = 0.9F;
            } else if (this.status == BroomEntity.Status.ON_LAND) {
                this.momentum = this.boatGlide;
                if (this.getControllingPassenger() instanceof Player) {
                    this.boatGlide /= 2.0F;
                }
            }

            Vec3 vector3d = this.getDeltaMovement();
            this.setDeltaMovement(vector3d.x * (double)this.momentum, vector3d.y + d1, vector3d.z * (double)this.momentum);
            this.deltaRotation *= this.momentum;
            if (d2 > 0.0D) {
                Vec3 vector3d1 = this.getDeltaMovement();
                this.setDeltaMovement(vector3d1.x, (vector3d1.y + d2 * 0.06153846016296973D) * 0.75D, vector3d1.z);
            }
        }
        if(this.isNoGravity() && this.getDeltaMovement().y() > 0)
            this.setDeltaMovement(this.getDeltaMovement().x(), this.getDeltaMovement().y()/1.5f < 0 ? 0 : this.getDeltaMovement().y()/1.5f, this.getDeltaMovement().z());
        if(this.isNoGravity() && this.getDeltaMovement().y() < 0)
            this.setDeltaMovement(this.getDeltaMovement().x(), this.getDeltaMovement().y()/1.5f > 0 ? 0 : this.getDeltaMovement().y()/1.5f, this.getDeltaMovement().z());

    }

    private void controlBoat() {

        Options settings = Minecraft.getInstance().options;
        boolean down = settings.keySprint.isDown();

        if (this.isVehicle()) {
            float f = 0.0F;
            if (this.leftInputDown) {
                --this.deltaRotation;
            }

            if (this.rightInputDown) {
                ++this.deltaRotation;
            }

            if (this.rightInputDown != this.leftInputDown && !this.forwardInputDown && !this.backInputDown) {
                f += 0.02F;
            }

            this.setYRot((float)this.getYRot() + this.deltaRotation);
            if (this.forwardInputDown) {
                f += 0.1F;
            }

            if (this.backInputDown) {
                f -= 0.02F;
            }
            if(this.jumpInputDown) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.15f * speedMultiplier, 0));
            }
            if(down) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.15f * speedMultiplier, 0));
            }
            this.setDeltaMovement(this.getDeltaMovement().add((double)(Mth.sin((float)-(this.getYRot() + 90) * ((float)Math.PI / 180F)) * f) * speedMultiplier, 0.0D, (double)(Mth.cos((float)(this.getYRot() + 90) * ((float)Math.PI / 180F)) * f) * speedMultiplier));
            this.setPaddleState(this.rightInputDown && !this.leftInputDown || this.forwardInputDown, this.leftInputDown && !this.rightInputDown || this.forwardInputDown);
        }
    }

    @Override
    public void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            float f = 0.0F;
            float f1 = (float)((this.isRemoved() ? (double)0.01F : this.getPassengersRidingOffset()) + passenger.getPassengersRidingOffset());
            if (this.getPassengers().size() > 1) {
                int i = this.getPassengers().indexOf(passenger);
                if (i == 0) {
                    f = 0.2F;
                } else {
                    f = -0.6F;
                }

                if (passenger instanceof Animal) {
                    f = (float)((double)f + 0.2D);
                }
            }

            Vec3 vector3d = (new Vec3((double)f, 0.0D, 0.0D)).yRot((float)-this.getYRot() * ((float)Math.PI / 180F) - ((float)Math.PI / 2F));
            passenger.setPos(this.getX() + vector3d.x, this.getY() + (double)f1, this.getZ() + vector3d.z);
            passenger.setYRot(passenger.getYRot() + this.deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
            this.applyYawToEntity(passenger);
            if (passenger instanceof Animal && this.getPassengers().size() > 1) {
                int j = passenger.getId() % 2 == 0 ? 90 : 270;
                passenger.setYBodyRot(((Animal)passenger).yBodyRot + (float)j);
                passenger.setYHeadRot(passenger.getYHeadRot() + (float)j);
            }

        }
    }
//
//    @Override
//    public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
//        Vec3 vector3d = getCollisionHorizontalEscapeVector((double)(this.getBbWidth() * Mth.SQRT_OF_TWO), (double)livingEntity.getBbWidth(), this.getYRot());
//        double d0 = this.getX() + vector3d.x;
//        double d1 = this.getZ() + vector3d.z;
//        BlockPos blockpos = new BlockPos(d0, this.getBoundingBox().maxY, d1);
//        BlockPos blockpos1 = blockpos.below();
//        if (!this.level.isWaterAt(blockpos1)) {
//            double d2 = (double)blockpos.getY() + this.level.getBlockFloorHeight(blockpos);
//            double d3 = (double)blockpos.getY() + this.level.getBlockFloorHeight(blockpos1);
//            Vec3 vec3 = new Vec3(d0, d2, d1);
//            Vec3 vec3_2 = new Vec3(d0, d3, d1);
//
//            for(Pose pose : livingEntity.getDismountPoses()) {
//                if (DismountHelper.canDismountTo(this.level, vec3, livingEntity, pose)) {
//                    livingEntity.setPose(pose);
//                    return vec3;
//                }
//
//                if (DismountHelper.canDismountTo(this.level, vec3_2, livingEntity, pose)) {
//                    livingEntity.setPose(pose);
//                    return vec3_2;
//                }
//            }
//        }
//
//        return super.getDismountLocationForPassenger(livingEntity);
//    }

    /**
     * Applies this boat's yaw to the given entity. Used to update the orientation of its passenger.
     */
    protected void applyYawToEntity(Entity entityToUpdate) {
        entityToUpdate.setYBodyRot(this.getYRot());
        float f = Mth.wrapDegrees(entityToUpdate.getYRot() - this.getYRot());
        float f1 = Mth.clamp(f, -105.0F, 105.0F);
        entityToUpdate.yRotO += f1 - f;
        entityToUpdate.setYRot(entityToUpdate.getYRot() + f1 - f);
        entityToUpdate.setYHeadRot(entityToUpdate.getYRot());
    }

    /**
     * Applies this entity's orientation (pitch/yaw) to another entity. Used to update passenger orientation.
     */
    @OnlyIn(Dist.CLIENT)
    @Override
    public void onPassengerTurned(Entity entityToUpdate) {
        this.applyYawToEntity(entityToUpdate);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
//        compound.putString("Type", this.getBoatType().getName());
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
//        if (compound.contains("Type", 8)) {
//            this.setBoatType(BroomEntity.Type.getTypeFromString(compound.getString("Type")));
//        }

    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        } else if (this.outOfControlTicks < 60.0F) {
            if (!this.level.isClientSide) {
                if(player.startRiding(this) ) {
                    //this.setDeltaMovement(getDeltaMovement().getX(), getDeltaMovement().getY() - 1, getDeltaMovement().getZ());
                    this.push(0,0.25,0);
                    return InteractionResult.CONSUME;
                }
                else {
                    return InteractionResult.PASS;
                }
            } else {
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
        this.lastYd = this.getDeltaMovement().y;
        if (!this.isPassenger()) {
            if (onGroundIn) {
                if (this.fallDistance > 3.0F) {
                    if (this.status != BroomEntity.Status.ON_LAND) {
                        this.fallDistance = 0.0F;
                        return;
                    }

                    this.causeFallDamage(this.fallDistance, 1.0F, DamageSource.FALL);
                    if (!this.level.isClientSide && !this.isRemoved()) {
                        this.remove(RemovalReason.DISCARDED);
                        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                            this.spawnAtLocation(ModItems.BROOM.get());
                        }
                    }
                }

                this.fallDistance = 0.0F;
            } else if (!this.level.getFluidState(this.getBlockPosBelowThatAffectsMyMovement()).is(FluidTags.WATER) && y < 0.0D) {
                this.fallDistance = (float)((double)this.fallDistance - y);
            }

        }
    }

    public boolean getPaddleState(int side) {
        return this.entityData.<Boolean>get(side == 0 ? LEFT_PADDLE : RIGHT_PADDLE) && this.getControllingPassenger() != null;
    }

    /**
     * Sets the damage taken from the last hit.
     */
    public void setDamageTaken(float damageTaken) {
        this.entityData.set(DAMAGE_TAKEN, damageTaken);
    }

    /**
     * Gets the damage taken from the last hit.
     */
    public float getDamageTaken() {
        return this.entityData.get(DAMAGE_TAKEN);
    }

    /**
     * Sets the time to count down from since the last time entity was hit.
     */
    public void setTimeSinceHit(int timeSinceHit) {
        this.entityData.set(TIME_SINCE_HIT, timeSinceHit);
    }

    /**
     * Gets the time since the last hit.
     */
    public int getTimeSinceHit() {
        return this.entityData.get(TIME_SINCE_HIT);
    }

    private void setRockingTicks(int ticks) {
        this.entityData.set(ROCKING_TICKS, ticks);
    }

    private int getRockingTicks() {
        return this.entityData.get(ROCKING_TICKS);
    }

    @OnlyIn(Dist.CLIENT)
    public float getRockingAngle(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevRockingAngle, this.rockingAngle);
    }

    /**
     * Sets the forward direction of the entity.
     */
    public void setForwardDirection(int forwardDirection) {
        this.entityData.set(FORWARD_DIRECTION, forwardDirection);
    }

    /**
     * Gets the forward direction of the entity.
     */
    public int getForwardDirection() {
        return this.entityData.get(FORWARD_DIRECTION);
    }

//    public void setBoatType(BroomEntity.Type boatType) {
//        this.entityData.set(BOAT_TYPE, boatType.ordinal());
//    }
//
//    public BroomEntity.Type getBoatType() {
//        return BroomEntity.Type.byId(this.entityData.get(BOAT_TYPE));
//    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2 && !this.isEyeInFluid(FluidTags.WATER);
    }

    /**
     * For vehicles, the first passenger is generally considered the controller and "drives" the vehicle. For example,
     * Pigs, Horses, and Boats are generally "steered" by the controlling passenger.
     */
    @Nullable
    @Override
    public Entity getControllingPassenger() {
        List<Entity> list = this.getPassengers();
        return list.isEmpty() ? null : list.get(0);
    }



    @OnlyIn(Dist.CLIENT)
    public void updateInputs(boolean leftInputDown, boolean rightInputDown, boolean forwardInputDown, boolean backInputDown, boolean jumpInputDown, boolean sneakingInputDown) {
        this.leftInputDown = leftInputDown;
        this.rightInputDown = rightInputDown;
        this.forwardInputDown = forwardInputDown;
        this.backInputDown = backInputDown;
        this.jumpInputDown = jumpInputDown;
        this.sneakingInputDown = sneakingInputDown;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public boolean canSwim() {
        return this.status == BroomEntity.Status.UNDER_WATER || this.status == BroomEntity.Status.UNDER_FLOWING_WATER;
    }

    // Forge: Fix MC-119811 by instantly completing lerp on board
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.isControlledByLocalInstance() && this.lerpSteps > 0) {
            this.lerpSteps = 0;
            this.absMoveTo(this.lerpX, this.lerpY, this.lerpZ, (float)this.getYRot(), (float)this.lerpPitch);
        }
    }

    public static enum Status {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR;
    }

    public enum AccelerationDirection
    {
        FORWARD, NONE, REVERSE,
        CHARGING;

        public static AccelerationDirection fromEntity(LivingEntity entity)
        {
            if(entity.yya > 0)
            {
                return FORWARD;
            }
            else if(entity.yya < 0)
            {
                return REVERSE;
            }
            return NONE;
        }
    }

    public static enum Type {
        OAK(Blocks.OAK_PLANKS, "oak"),
        SPRUCE(Blocks.SPRUCE_PLANKS, "spruce"),
        BIRCH(Blocks.BIRCH_PLANKS, "birch"),
        JUNGLE(Blocks.JUNGLE_PLANKS, "jungle"),
        ACACIA(Blocks.ACACIA_PLANKS, "acacia"),
        DARK_OAK(Blocks.DARK_OAK_PLANKS, "dark_oak");

        private final String name;
        private final Block block;

        private Type(Block block, String name) {
            this.name = name;
            this.block = block;
        }

        public String getName() {
            return this.name;
        }

        public String toString() {
            return this.name;
        }

        /**
         * Get a boat type by it's enum ordinal
         */
        public static BroomEntity.Type byId(int id) {
            BroomEntity.Type[] aboatentity$type = values();
            if (id < 0 || id >= aboatentity$type.length) {
                id = 0;
            }

            return aboatentity$type[id];
        }

        public static BroomEntity.Type getTypeFromString(String nameIn) {
            BroomEntity.Type[] aboatentity$type = values();

            for(int i = 0; i < aboatentity$type.length; ++i) {
                if (aboatentity$type[i].getName().equals(nameIn)) {
                    return aboatentity$type[i];
                }
            }

            return aboatentity$type[0];
        }
    }
}
