package keystrokesmod.module;

import keystrokesmod.module.impl.client.ChatCommands;
import keystrokesmod.module.impl.client.CommandLine;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.combat.*;
import keystrokesmod.module.impl.fun.*;
import keystrokesmod.module.impl.minigames.*;
import keystrokesmod.module.impl.movement.*;
import keystrokesmod.module.impl.other.*;
import keystrokesmod.module.impl.player.*;
import keystrokesmod.module.impl.render.*;
import keystrokesmod.module.impl.world.*;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.profile.Manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public static List<Module> modules = new ArrayList<>();
    public static List<Module> organizedModules = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Module> modulesByName = new HashMap<>();
    private static final Map<Class<?>, Module> modulesByClass = new HashMap<>();

    public static NameHider nameHider;
    public static FastPlace fastPlace;
    public static MurderMystery murderMystery;
    public static InvMove invmove;
    public static SkyWars skyWars;
    public static AntiFireball antiFireball;
    public static AutoSwap autoSwap;
    public static BedAura bedAura;
    public static FastMine fastMine;
    public static AntiShuffle antiShuffle;
    public static CommandLine commandLine;
    public static LongJump longJump;
    public static AntiBot antiBot;
    public static NoSlow noSlow;
    public static KillAura killAura;
    public static AutoClicker autoClicker;
    public static HitBox hitBox;
    public static Reach reach;
    public static NoRotate noRotate;
    public static BedESP bedESP;
    public static Blink blink;
    public static Chams chams;
    public static HUD hud;
    public static Timer timer;
    public static Fly fly;
    public static WTap wTap;
    public static Velocity velocity;
    public static AntiDebuff antiDebuff;
    public static TargetHUD targetHUD;
    public static NoFall noFall;
    public static PlayerESP playerESP;
    public static Reduce reduce;
    public static SafeWalk safeWalk;
    public static KeepSprint keepSprint;
    public static Piercing piercing;
    public static GhostHand ghostHand;
    public static AntiKnockback antiKnockback;
    public static ExtendCamera extendCamera;
    public static Freelook freelook;
    public static InvManager invManager;
    public static Tower tower;
    public static NoCameraClip noCameraClip;
    public static BedWars bedwars;
    public static BHop bHop;
    public static NoHurtCam noHurtCam;
    public static Scaffold scaffold;
    public static AutoTool autoTool;
    public static Sprint sprint;
    public static Weather weather;
    public static ChatCommands chatCommands;
    public static BlockIn blockIn;

    public void register() {
        this.addModule(chatCommands = new ChatCommands());
        this.addModule(commandLine = new CommandLine());
        this.addModule(new Gui());
        this.addModule(new Settings());

        this.addModule(new AimAssist());
        this.addModule(antiKnockback = new AntiKnockback());
        this.addModule(autoClicker = new AutoClicker());
        this.addModule(new Autoblock());
        this.addModule(blockIn = new BlockIn());
        this.addModule(new BurstClicker());
        this.addModule(new ClickAssist());
        this.addModule(hitBox = new HitBox());
        this.addModule(new JumpReset());
        this.addModule(killAura = new KillAura());
        this.addModule(piercing = new Piercing());
        this.addModule(ghostHand = new GhostHand());
        this.addModule(new RawInput());
        this.addModule(reach = new Reach());
        this.addModule(reduce = new Reduce());
        this.addModule(new RodAimbot());
        this.addModule(new TPAura());
        this.addModule(velocity = new Velocity());
        this.addModule(wTap = new WTap());

        this.addModule(new ExtraBobbing());
        this.addModule(new FlameTrail());
        this.addModule(new SlyPort());
        this.addModule(new Spin());

        this.addModule(new AutoRequeue());
        this.addModule(new AutoWho());
        this.addModule(bedwars = new BedWars());
        this.addModule(new BridgeInfo());
        this.addModule(new DuelsStats());
        this.addModule(murderMystery = new MurderMystery());
        this.addModule(skyWars = new SkyWars());
        this.addModule(new SpeedBuilders());
        this.addModule(new SumoFences());
        this.addModule(new WoolWars());

        this.addModule(bHop = new BHop());
        this.addModule(new Boost());
        this.addModule(new Dolphin());
        this.addModule(fly = new Fly());
        this.addModule(invmove = new InvMove());
        this.addModule(keepSprint = new KeepSprint());
        this.addModule(longJump = new LongJump());
        this.addModule(noSlow = new NoSlow());
        this.addModule(new Speed());
        this.addModule(sprint = new Sprint());
        this.addModule(new StopMotion());
        this.addModule(new Teleport());
        this.addModule(timer = new Timer());
        this.addModule(new VClip());

        this.addModule(new Anticheat());
        this.addModule(new ChatBypass());
        this.addModule(new FakeChat());
        this.addModule(new LatencyAlerts());
        this.addModule(nameHider = new NameHider());
        this.addModule(new ViewPackets());

        this.addModule(new AntiAFK());
        this.addModule(antiFireball = new AntiFireball());
        this.addModule(new AutoJump());
        this.addModule(new BridgeAssist());
        this.addModule(autoSwap = new AutoSwap());
        this.addModule(autoTool = new AutoTool());
        this.addModule(bedAura = new BedAura());
        this.addModule(blink = new Blink());
        this.addModule(new DelayRemover());
        this.addModule(fastMine = new FastMine());
        this.addModule(fastPlace = new FastPlace());
        this.addModule(new FakeLag());
        this.addModule(new Freecam());
        this.addModule(invManager = new InvManager());
        this.addModule(noFall = new NoFall());
        this.addModule(noRotate = new NoRotate());
        this.addModule(safeWalk = new SafeWalk());
        this.addModule(scaffold = new Scaffold());
        this.addModule(tower = new Tower());
        this.addModule(new WaterBucket());

        this.addModule(new Manager());

        this.addModule(antiDebuff = new AntiDebuff());
        this.addModule(antiShuffle = new AntiShuffle());
        this.addModule(new Arrows());
        this.addModule(bedESP = new BedESP());
        this.addModule(new BlockOverlay());
        this.addModule(new BreakProgress());
        this.addModule(chams = new Chams());
        this.addModule(new DamageTint());
        this.addModule(new ChestESP());
        this.addModule(extendCamera = new ExtendCamera());
        this.addModule(freelook = new Freelook());
        this.addModule(new FallView());
        this.addModule(new Holdlook());
        this.addModule(hud = new HUD());
        this.addModule(new Indicators());
        this.addModule(new ItemESP());
        this.addModule(new ItemPhysics());
        this.addModule(new MobESP());
        // Legacy 2D Nametags module is deprecated in favor of the 3D-based replacement.
        // this.addModule(new Nametags());
        this.addModule(new Nametags3D());
        this.addModule(noCameraClip = new NoCameraClip());
        this.addModule(noHurtCam = new NoHurtCam());
        this.addModule(playerESP = new PlayerESP());
        this.addModule(new Radar());
        this.addModule(new Saturation());
        this.addModule(targetHUD = new TargetHUD());
        this.addModule(new Trajectories());
        this.addModule(new TNTTimer());
        this.addModule(new Tracers());
        this.addModule(new Xray());

        this.addModule(antiBot = new AntiBot());
        this.addModule(weather = new Weather());

        this.addModule(new keystrokesmod.script.Manager());
        
        antiBot.enable();
        modules.sort(Comparator.comparing(Module::getName));
    }

    public void addModule(Module m) {
        modules.add(m);
        modulesByName.put(m.getName(), m);
        modulesByClass.put(m.getClass(), m);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> inCategory(Module.category category) {
        ArrayList<Module> categoryModules = new ArrayList<>();

        for (Module mod : this.getModules()) {
            if (mod.moduleCategory().equals(category)) {
                categoryModules.add(mod);
            }
        }

        return categoryModules;
    }

    public static Module getModule(String moduleName) {
        return modulesByName.get(moduleName);
    }

    public static Module getModule(Class<?> clazz) {
        return modulesByClass.get(clazz);
    }

    public static void sort() {
        if (HUD.alphabeticalSort.isToggled()) {
            organizedModules.sort(Comparator.comparing(Module::getNameInHud));
        }
        else {
            organizedModules.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2.getNameInHud() + ((HUD.showInfo.isToggled() && !o2.getInfo().isEmpty()) ? " " + o2.getInfo() : "")) - Utils.mc.fontRendererObj.getStringWidth(o1.getNameInHud() + (HUD.showInfo.isToggled() && !o1.getInfo().isEmpty() ? " " + o1.getInfo() : "")));
        }
    }

    public static boolean canExecuteChatCommand() {
        return ModuleManager.chatCommands != null && ModuleManager.chatCommands.isEnabled();
    }

    public static boolean lowercaseChatCommands() {
        return ModuleManager.chatCommands != null && ModuleManager.chatCommands.isEnabled() && ModuleManager.chatCommands.lowercase();
    }
}