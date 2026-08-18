package keystrokesmod.gui;

import keystrokesmod.utility.AccountManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiAccountManager extends GuiScreen {

    private static final int BUTTON_LOGIN_OFFLINE = 1;
    private static final int BUTTON_LOGIN_TOKEN = 2;
    private static final int BUTTON_DONE = 0;

    public GuiScreen parentScreen;

    private GuiTextField offlineName;
    private GuiTextField tokenName;
    private GuiTextField tokenField;

    private volatile String status = "";
    private volatile boolean statusSuccess = true;
    private boolean busy = false;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int fieldX = this.width / 2 - 60;
        int fieldWidth = 200;

        this.offlineName = new GuiTextField(11, this.fontRendererObj, fieldX, 60, fieldWidth, 20);
        this.offlineName.setMaxStringLength(16);

        this.tokenName = new GuiTextField(12, this.fontRendererObj, fieldX, 110, fieldWidth, 20);
        this.tokenName.setMaxStringLength(16);

        this.tokenField = new GuiTextField(13, this.fontRendererObj, fieldX, 140, fieldWidth, 20);
        this.tokenField.setMaxStringLength(512);

        this.buttonList.add(new GuiButton(BUTTON_LOGIN_OFFLINE, this.width / 2 + 145, 60, 100, 20, "Login Offline"));
        this.buttonList.add(new GuiButton(BUTTON_LOGIN_TOKEN, this.width / 2 + 145, 140, 100, 20, "Login with Token"));
        this.buttonList.add(new GuiButton(BUTTON_DONE, this.width / 2 + 4 + 76, this.height - 28, 75, 20, "Done"));

        this.offlineName.setFocused(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRendererObj, "Account Manager", this.width / 2, 15, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, "Current account: " + AccountManager.getCurrentUsername(),
                this.width / 2, 28, 0xAAAAAA);

        int labelX = this.width / 2 - 150;

        this.drawString(this.fontRendererObj, "Cracked / Offline", labelX, 46, 0x55FFFF);
        this.drawString(this.fontRendererObj, "Username:", labelX, 65, 0xAAAAAA);

        this.drawString(this.fontRendererObj, "Session Token", labelX, 96, 0x55FFFF);
        this.drawString(this.fontRendererObj, "Username:", labelX, 115, 0xAAAAAA);
        this.drawString(this.fontRendererObj, "Token:", labelX, 145, 0xAAAAAA);

        this.offlineName.drawTextBox();
        this.tokenName.drawTextBox();
        this.tokenField.drawTextBox();

        int statusColor = this.statusSuccess ? 0x55FF55 : 0xFF5555;
        this.drawCenteredString(this.fontRendererObj, this.status, this.width / 2, this.height - 45, statusColor);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (this.busy) {
            return;
        }
        switch (button.id) {
            case BUTTON_LOGIN_OFFLINE:
                this.busy = true;
                this.setBusyState();
                String name = this.offlineName.getText();
                if (name.isEmpty()) {
                    this.status = "Enter a username";
                    this.statusSuccess = false;
                    this.busy = false;
                    this.setBusyState();
                    return;
                }
                AccountManager.loginCracked(name);
                this.status = "Logged in as " + name;
                this.statusSuccess = true;
                this.busy = false;
                this.setBusyState();
                break;
            case BUTTON_LOGIN_TOKEN:
                String tokenUsername = this.tokenName.getText();
                String token = this.tokenField.getText();
                if (tokenUsername.isEmpty() || token.isEmpty()) {
                    this.status = "Enter a username and token";
                    this.statusSuccess = false;
                    return;
                }
                this.busy = true;
                this.setBusyState();
                this.status = "Validating token...";
                this.statusSuccess = true;
                AccountManager.loginToken(tokenUsername, token, result -> {
                    this.status = result;
                    this.statusSuccess = result.startsWith("Logged in");
                    this.busy = false;
                    this.setBusyState();
                });
                break;
            case BUTTON_DONE:
                this.mc.displayGuiScreen(new GuiMultiplayer(this.parentScreen));
                break;
        }
    }

    private void setBusyState() {
        for (GuiButton button : this.buttonList) {
            button.enabled = !this.busy;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiMultiplayer(this.parentScreen));
            return;
        }
        if (this.offlineName.textboxKeyTyped(typedChar, keyCode)
                || this.tokenName.textboxKeyTyped(typedChar, keyCode)
                || this.tokenField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.offlineName.mouseClicked(mouseX, mouseY, mouseButton);
        this.tokenName.mouseClicked(mouseX, mouseY, mouseButton);
        this.tokenField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        this.offlineName.updateCursorCounter();
        this.tokenName.updateCursorCounter();
        this.tokenField.updateCursorCounter();
    }
}
