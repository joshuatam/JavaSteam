package in.dragonbra.javasteamsamples._032_achievements;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.authentication.AuthenticationException;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged;
import in.dragonbra.javasteam.steam.authentication.QrAuthSession;
import in.dragonbra.javasteam.steam.authentication.SteamAuthentication;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.AchievementBlocks;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.callback.UserStatsCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.types.SteamID;
import in.dragonbra.javasteam.util.log.DefaultLogListener;
import in.dragonbra.javasteam.util.log.LogManager;
import pro.leaco.console.qrcode.ConsoleQrcode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Sample 32 (QR variant): Steam Achievements via QR Authentication.
 * Demonstrates retrieving achievement data for a Steam game using
 * UserStatsCallback, authenticated via the Steam Mobile App QR code flow
 * (no username or password required).
 */
@SuppressWarnings("FieldCanBeLocal")
public class SampleAchievementsWithQR implements Runnable, IChallengeUrlChanged {

    // Default to Team Fortress 2 (Free to play game with achievements)
    // Other free games with achievements you can try:
    // - 730 (CS:GO/CS2)
    // - 440 (Team Fortress 2)
    // - 570 (Dota 2)
    // - 49520 (Borderlands 2 - requires ownership)
    private static final int DEFAULT_APP_ID = 440;

    private SteamClient steamClient;
    private CallbackManager manager;
    private SteamUser steamUser;
    private SteamUserStats steamUserStats;
    private boolean isRunning;
    private final int appId;
    private SteamID currentUserSteamID;

    public SampleAchievementsWithQR(int appId) {
        this.appId = appId;
    }

    public static void main(String[] args) {
        int appId = DEFAULT_APP_ID;
        if (args.length >= 1) {
            try {
                appId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid App ID, using default: " + DEFAULT_APP_ID);
            }
        }

        System.out.println("Sample 032 (QR): Steam Achievements via QR Authentication");
        System.out.println("Usage: sample032qr [appid]");
        System.out.println("  appid: Optional Steam App ID (default: 440 - Team Fortress 2)");
        System.out.println();

        LogManager.addListener(new DefaultLogListener());
        new SampleAchievementsWithQR(appId).run();
    }

    @Override
    public void run() {
        steamClient = new SteamClient();
        manager = new CallbackManager(steamClient);
        steamUser = steamClient.getHandler(SteamUser.class);
        steamUserStats = steamClient.getHandler(SteamUserStats.class);

        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);
        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);
        manager.subscribe(UserStatsCallback.class, this::onUserStats);

        isRunning = true;
        System.out.println("Connecting to Steam...");
        steamClient.connect();

        while (isRunning) {
            manager.runWaitCallbacks(1000L);
        }
    }

    private void onConnected(ConnectedCallback callback) {
        System.out.println("Connected! Starting QR authentication...");
        System.out.println();

        try {
            SteamAuthentication auth = new SteamAuthentication(steamClient);
            AuthSessionDetails authDetails = new AuthSessionDetails();

            QrAuthSession authSession = auth.beginAuthSessionViaQR(authDetails).get();

            // Steam refreshes the challenge URL periodically; onChanged() below redraws it.
            authSession.setChallengeUrlChanged(this);

            // Draw the initial QR code immediately.
            drawQRCode(authSession);

            // Block until the user scans the QR code and approves the login on the mobile app.
            AuthPollResult pollResponse = authSession.pollingWaitForResult().get();

            System.out.println("QR authentication successful! Logging in as " + pollResponse.getAccountName() + "...");

            LogOnDetails details = new LogOnDetails();
            details.setUsername(pollResponse.getAccountName());
            details.setAccessToken(pollResponse.getRefreshToken());
            details.setLoginID(149);

            steamUser.logOn(details);
        } catch (Exception e) {
            if (e instanceof AuthenticationException) {
                System.err.println("Authentication error: " + e.getMessage());
            } else if (e instanceof CancellationException) {
                System.err.println("Timeout waiting for QR scan: " + e.getMessage());
            } else {
                System.err.println("Error: " + e.getMessage());
            }
            isRunning = false;
        }
    }

    private void onDisconnected(DisconnectedCallback callback) {
        System.out.println("Disconnected from Steam");
        isRunning = false;
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() != EResult.OK) {
            System.out.println("Unable to logon: " + callback.getResult() + " / " + callback.getExtendedResult());
            isRunning = false;
            return;
        }

        currentUserSteamID = callback.getClientSteamID();
        System.out.println("Logged on! SteamID: " + currentUserSteamID.convertToUInt64());
        System.out.println();
        System.out.println("Requesting achievement data for App ID: " + appId + "...");

        steamUserStats.getUserStats(appId, currentUserSteamID);
    }

    //! This is where the meat of the Sample is.
    private void onUserStats(UserStatsCallback callback) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ACHIEVEMENT DATA RECEIVED");
        System.out.println("=".repeat(80) + "\n");

        System.out.println("Result: " + callback.getResult());
        System.out.println("Game ID: " + callback.getGameId());

        if (callback.getResult() != EResult.OK) {
            System.err.println("\nFailed to get achievements: " + callback.getResult());
            System.err.println("This could mean:");
            System.err.println("  - The game doesn't have Steam achievements");
            System.err.println("  - You don't own the game");
            System.err.println("  - The game hasn't been launched yet (required for some games)");
            System.err.println("  - Steam servers are having issues");
            steamClient.disconnect();
            return;
        }

        // Get the raw achievement blocks
        List<AchievementBlocks> blocks = callback.getAchievementBlocks();
        System.out.println("\nAchievement Blocks (Base Game + DLCs): " + blocks.size());

        // There can be multiple blocks when you grab an appId. (Usually Game + DLC/Expansions)
        // AchievementBlocks are filtered into unlockTimes
        // Timestamp exists? -> Unlocked
        // Timestamp does not exist? -> Locked
        // Timestamps are in an array and are sorted by the ID of the specific achievement
        for (int i = 0; i < blocks.size(); i++) {
            AchievementBlocks block = blocks.get(i);
            long unlocked = block.getUnlockTime().stream().filter(t -> t > 0).count();
            System.out.println("  Block " + (i + 1) + " (ID " + block.getAchievementId() + "): "
                    + unlocked + "/" + block.getUnlockTime().size() + " unlocked");
        }

        // Get the expanded individual achievements
        List<AchievementBlocks> achievements = callback.getExpandedAchievements();

        System.out.println("\nTotal Individual Achievements: " + achievements.size());

        long unlockedCount = achievements.stream().filter(AchievementBlocks::isUnlocked).count();
        System.out.println("Unlocked: " + unlockedCount);
        System.out.println("Locked: " + (achievements.size() - unlockedCount));
        System.out.println("Completion: " + String.format("%.1f%%", (unlockedCount * 100.0 / achievements.size())));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("ACHIEVEMENT DETAILS");
        System.out.println("=".repeat(80) + "\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < achievements.size(); i++) {
            AchievementBlocks achievement = achievements.get(i);

            System.out.println(String.format("[%d/%d] %s",
                    i + 1,
                    achievements.size(),
                    achievement.getDisplayName() != null ? achievement.getDisplayName()
                    : (achievement.getName() != null ? achievement.getName() : "Achievement #" + achievement.getAchievementId())
            ));

            if (achievement.getDescription() != null && !achievement.getDescription().isEmpty()) {
                System.out.println("  \"" + achievement.getDescription() + "\"");
            }

            System.out.println("  Status: " + (achievement.isUnlocked() ? "✓ UNLOCKED" : "✗ LOCKED"));

            if (achievement.isUnlocked() && achievement.getUnlockTimestamp() > 0) {
                String unlockDate = dateFormat.format(new Date(achievement.getUnlockTimestamp() * 1000L));
                System.out.println("  Unlocked: " + unlockDate);
            }

            if (achievement.getHidden()) {
                System.out.println("  [Hidden Achievement]");
            }

            if (achievement.getIcon() != null && !achievement.getIcon().isEmpty()) {
                String iconUrl = "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/"
                        + callback.getGameId() + "/" + achievement.getIcon();
                System.out.println("  Icon: " + iconUrl);
            }

            System.out.println();
        }

        System.out.println("=".repeat(80));
        steamClient.disconnect();
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        System.out.println("Logged off: " + callback.getResult());
        isRunning = false;
    }

    private void drawQRCode(QrAuthSession authSession) {
        String challengeURL = authSession.getChallengeUrl();
        System.out.println("Challenge URL: " + challengeURL);
        System.out.println();
        System.out.println("Scan this QR code with your Steam Mobile App:");
        ConsoleQrcode.INSTANCE.print(challengeURL);
    }

    /** Called by Steam when the challenge URL is refreshed (~30 s intervals). */
    @Override
    public void onChanged(QrAuthSession qrAuthSession) {
        System.out.println("QR code refreshed — please scan the new code:");
        drawQRCode(qrAuthSession);
    }
}
