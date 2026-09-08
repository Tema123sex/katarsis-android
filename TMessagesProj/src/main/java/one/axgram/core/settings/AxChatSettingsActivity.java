package one.axgram.core.settings;

import static one.axgram.core.settings.BaseAxSettingsActivity.PARTIAL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;
import one.axgram.core.AxCoreConfig;
import one.axgram.core.config.CellGroup;
import one.axgram.core.config.cell.AbstractConfigCell;
import one.axgram.core.config.cell.ConfigCellCustom;
import one.axgram.core.config.cell.ConfigCellDivider;
import one.axgram.core.config.cell.ConfigCellHeader;
import one.axgram.core.config.cell.ConfigCellSelectBox;
import one.axgram.core.config.cell.ConfigCellTextCheck;
import one.axgram.core.config.cell.ConfigCellTextDetail;
import one.axgram.core.config.cell.ConfigCellTextInput;
import one.axgram.core.helpers.remote.EmojiHelper;
import one.axgram.core.ui.PopupBuilder;
import one.axgram.extra.AxConfig;
import one.axgram.extra.helper.DoubleTap;

@SuppressLint("RtlHardcoded")
public class AxChatSettingsActivity extends BaseAxLegacySettingsActivity implements NotificationCenter.NotificationCenterDelegate, EmojiHelper.EmojiPacksLoadedListener {

    private final CellGroup a = cellGroup = new CellGroup(this);

    // Sticker Size
    private final AbstractConfigCell header0 = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("StickerSize")));
    private final AbstractConfigCell stickerSizeRow = cellGroup.appendCell(new ConfigCellCustom("StickerSize", ConfigCellCustom.CUSTOM_ITEM_StickerSize, true));
    private final AbstractConfigCell divider0 = cellGroup.appendCell(new ConfigCellDivider());

    // Chats
    private final AbstractConfigCell header1 = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("Chat")));
    // AxGram: anti-delete + anti self-destruct viewer entries
    private final AbstractConfigCell axDeletedMessagesRow = cellGroup.appendCell(new one.axgram.core.config.cell.ConfigCellText("AxDeletedMessages", () -> presentFragment(new one.axgram.core.AxDeletedMessagesActivity())));
    private final AbstractConfigCell axOnceMessagesRow = cellGroup.appendCell(new one.axgram.core.config.cell.ConfigCellText("AxOnceMessages", () -> presentFragment(new one.axgram.core.AxDeletedMessagesActivity(0, true))));
    private final AbstractConfigCell emojiSetsRow = cellGroup.appendCell(new ConfigCellCustom("EmojiSet", ConfigCellCustom.CUSTOM_ITEM_EmojiSet, true));
    private final AbstractConfigCell unreadBadgeOnBackButton = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.unreadBadgeOnBackButton));
    private final AbstractConfigCell sendCommentAfterForwardRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.sendCommentAfterForward));
    private final AbstractConfigCell showRecentForwardTabRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowRecentForwardTab()));
    private final AbstractConfigCell useChatAttachMediaMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.useChatAttachMediaMenu, LocaleController.getString("UseChatAttachEnterMenuNotice")));
    private final AbstractConfigCell disableLinkPreviewByDefaultRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableLinkPreviewByDefault, LocaleController.getString("DisableLinkPreviewByDefaultNotice")));
    private final AbstractConfigCell takeGIFasVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.takeGIFasVideo));
    private final AbstractConfigCell showSmallGifRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowSmallGIF()));
    private final AbstractConfigCell showSeconds = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.showSeconds));
    private final AbstractConfigCell showBottomActionsWhenSelectingRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.showBottomActionsWhenSelecting));
    private final AbstractConfigCell labelChannelUserRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.labelChannelUser));
    private final AbstractConfigCell hideSendAsChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.hideSendAsChannel));
    private final AbstractConfigCell showSpoilersDirectlyRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.showSpoilersDirectly));
    private final AbstractConfigCell messageMenuRow = cellGroup.appendCell(new ConfigCellSelectBox("MessageMenu", null, null, () -> {
        if (getParentActivity() == null) return;
        showMessageMenuAlert();
    }));
    private final AbstractConfigCell defaultDeleteMenuRow = cellGroup.appendCell(new ConfigCellSelectBox("DefaultDeleteMenu", null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), AxConfig.INSTANCE.getDefaultDeleteMenu().getKey(), new ArrayList<>() {{
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDefaultDeleteMenuBanUsers()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDefaultDeleteMenReportSpam()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDefaultDeleteMenuDeleteAll()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDefaultDeleteMenuDeleteAllReactions()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups()));
        }}));
    }));
    private final AbstractConfigCell disableActionBarButtonRow = cellGroup.appendCell(new ConfigCellSelectBox(AxConfig.INSTANCE.getDisableActionBarButton().getKey(), null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), AxConfig.INSTANCE.getDisableActionBarButton().getKey(), new ArrayList<>() {{
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableActionBarButtonReply()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableActionBarButtonEdit()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableActionBarButtonSelectBetween()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableActionBarButtonCopy()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableActionBarButtonForward()));
        }}));
    }));
    private final AbstractConfigCell customGreatRow = cellGroup.appendCell(new ConfigCellTextInput(null, AxConfig.INSTANCE.getCustomGreat(), LocaleController.getString(R.string.CustomGreatHint), null,(input) -> input.isEmpty() ? (String) AxConfig.INSTANCE.getCustomGreat().defaultValue : input));
    private final AbstractConfigCell customPoorRow = cellGroup.appendCell(new ConfigCellTextInput(null, AxConfig.INSTANCE.getCustomPoor(), LocaleController.getString(R.string.CustomPoorHint), null,(input) -> input.isEmpty() ? (String) AxConfig.INSTANCE.getCustomPoor().defaultValue : input));
    private final AbstractConfigCell showEditedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowEditedIcon()));
    private final AbstractConfigCell customEditedMessageRow = cellGroup.appendCell(new ConfigCellTextInput(null, AxConfig.INSTANCE.getCustomEditedMessage(), "", null));
    private final AbstractConfigCell showServicesTime = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowServicesTime()));
//    private final AbstractConfigCell combineMessageRow = cellGroup.appendCell(new ConfigCellSelectBox(null, AxConfig.INSTANCE.getCombineMessage(), new String[]{
//            LocaleController.getString("combineMessageEnabledWithReply", R.string.CombineMessageEnabledWithReply),
//            LocaleController.getString("combineMessageEnabled", R.string.CombineMessageEnabled),
//            LocaleController.getString("combineMessageDisabled", R.string.CombineMessageDisabled)
//    }, null));
    private final AbstractConfigCell dateOfForwardMsgRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDateOfForwardedMsg()));
    private final AbstractConfigCell showMessageIDRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowMessageID()));
    private final AbstractConfigCell showPremiumStarInChatRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowPremiumStarInChat()));
    private final AbstractConfigCell showPremiumAvatarAnimationRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowPremiumAvatarAnimation()));
    private final AbstractConfigCell alwaysSaveChatOffsetRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getAlwaysSaveChatOffset()));
    private final AbstractConfigCell autoInsertGIFCaptionRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getAutoInsertGIFCaption()));
    private final AbstractConfigCell defaultMonoLanguageRow = cellGroup.appendCell(new ConfigCellTextInput(null, AxConfig.INSTANCE.getDefaultMonoLanguage(),
            null, null,
            (input) -> input.isEmpty() ? (String) AxConfig.INSTANCE.getDefaultMonoLanguage().defaultValue : input));
    private final AbstractConfigCell disableGlobalSearchRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableGlobalSearch()));
//    private final AbstractConfigCell mapPreviewRow = cellGroup.appendCell(new ConfigCellSelectBox(null, AxCoreConfig.mapPreviewProvider,
//            new String[]{
//                    LocaleController.getString("MapPreviewProviderTelegram", R.string.MapPreviewProviderTelegram),
//                    LocaleController.getString("MapPreviewProviderYandex", R.string.MapPreviewProviderYandex),
//                    LocaleController.getString("MapPreviewProviderNobody", R.string.MapPreviewProviderNobody)
//            }, null));
    private final AbstractConfigCell doubleTapActionRow = cellGroup.appendCell(new ConfigCellCustom("DoubleTapAction", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell autoReplaceRepeatRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getAutoReplaceRepeat()));
    private final AbstractConfigCell textStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("TextStyle", null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), "TextStyle", new ArrayList<>() {{
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextBold(), null, LocaleController.getString(R.string.Bold)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextItalic(), null, LocaleController.getString(R.string.Italic)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextMono(), null, LocaleController.getString(R.string.Mono)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextStrikethrough(), null, LocaleController.getString(R.string.Strike)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextUnderline(), null, LocaleController.getString(R.string.Underline)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextSpoiler(), null, LocaleController.getString(R.string.Spoiler)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextCreateLink(), null, LocaleController.getString(R.string.CreateLink)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextCreateMention(), null, LocaleController.getString(R.string.CreateMention)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextRegular(), null, LocaleController.getString(R.string.Regular)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextUndoRedo(), null, LocaleController.getString(R.string.TextUndoRedo)));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTextQuote(), null, LocaleController.getString(R.string.Quote)));
        }}));
    }));
    private final AbstractConfigCell disableZalgoSymbolsRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getZalgoFilter(), LocaleController.getString("ZalgoFilterNotice", R.string.ZalgoFilterNotice)));
    private final AbstractConfigCell quickToggleAnonymousRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getQuickToggleAnonymous(), LocaleController.getString("QuickToggleAnonymousNotice", R.string.QuickToggleAnonymousNotice)));
    private final AbstractConfigCell showOnlineStatusRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowOnlineStatus(), LocaleController.getString("ShowOnlineStatusNotice", R.string.ShowOnlineStatusNotice)));
    private final AbstractConfigCell showRecentOnlineStatusRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowRecentOnlineStatus()));
    private final AbstractConfigCell disableCustomWallpaperUserRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableCustomWallpaperUser()));
    private final AbstractConfigCell disableCustomWallpaperChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableCustomWallpaperChannel()));
    private final AbstractConfigCell coloredAdminTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getColoredAdminTitle()));
    private final AbstractConfigCell disableRepeatInChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableRepeatInChannel()));
    private final AbstractConfigCell showVoteCountBeforeVoteRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowVoteCountBeforeVote()));
    private final AbstractConfigCell dividerChat = cellGroup.appendCell(new ConfigCellDivider());

    // Interactions
    private final AbstractConfigCell headerInteractions = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("InteractionSettings")));
    private final AbstractConfigCell hideKeyboardOnChatScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.hideKeyboardOnChatScroll));
    private final AbstractConfigCell rearVideoMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.rearVideoMessages));
    private final AbstractConfigCell disableInstantCameraRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableInstantCamera));
    private final AbstractConfigCell hideInstantCameraRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getHideInstantCamera()));
    private final AbstractConfigCell disableVibrationRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableVibration));
    private final AbstractConfigCell disableProximityEventsRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableProximityEvents));
    private final AbstractConfigCell disableTrendingRow = cellGroup.appendCell(new ConfigCellSelectBox("DisableTrending", null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), "DisableTrending", new ArrayList<>() {{
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumUpgrade()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumExpiring()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumChristmas()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableBirthdayContact()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumRestore()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableStarsSubscription()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableFavoriteSearchEmojiTags()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableFeatuerdEmojis()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableFeaturedStickers()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableFeaturedGifs()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumFavoriteEmojiTags()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableNonPremiumChannelChatShow()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableShortcutTagActions()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePhoneSharePrompt()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePremiumSendTodo()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableEmptyStarButton()));
            add(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableGifts()));
        }}));
    }));
    private final AbstractConfigCell disableSwipeToNextRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableSwipeToNext));
    private final AbstractConfigCell disableChannelMuteButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableChannelMuteButton()));
    private final AbstractConfigCell disablePhotoSideActionRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disablePhotoSideAction));
    private final AbstractConfigCell disableClickProfileGalleryViewRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableClickProfileGalleryView()));
    private final AbstractConfigCell disableRemoteEmojiInteractionsRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableRemoteEmojiInteractions));
    private final AbstractConfigCell rememberAllBackMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.rememberAllBackMessages));
    //    private final AbstractConfigCell reactionsRow = cellGroup.appendCell(new ConfigCellSelectBox(null, AxCoreConfig.reactions,
//            new String[]{
//                    LocaleController.getString("doubleTapSendReactions", R.string.doubleTapSendReactions),
//                    LocaleController.getString("doubleTapShowReactions", R.string.doubleTapShowReactions),
//                    LocaleController.getString("ReactionsDisabled", R.string.ReactionsDisabled),
//            }, null));
    private final AbstractConfigCell showFullAboutRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowFullAbout()));
    private final AbstractConfigCell hideMessageSeenTooltipcRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getHideMessageSeenTooltip()));
    private final AbstractConfigCell typeMessageHintUseGroupNameRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getTypeMessageHintUseGroupName()));
    private final AbstractConfigCell showSendAsUnderMessageHintRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowSendAsUnderMessageHint()));
    private final AbstractConfigCell hideBotButtonInInputFieldRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getHideBotButtonInInputField()));
    private final AbstractConfigCell doNotUnarchiveBySwipeRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDoNotUnarchiveBySwipe()));
    private final AbstractConfigCell disableMarkdownRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableMarkdown()));
    private final AbstractConfigCell newMarkdownParserRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getNewMarkdownParser()));
    private final AbstractConfigCell markdownParseLinksRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getMarkdownParseLinks()));
    private final AbstractConfigCell disableClickCommandToSendRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableClickCommandToSend(), LocaleController.getString(R.string.DisableClickCommandToSendHint)));
    private final AbstractConfigCell showQuickReplyInBotCommandsRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowQuickReplyInBotCommands()));
    private final AbstractConfigCell disablePreviewVideoSoundShortcutRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisablePreviewVideoSoundShortcut(), LocaleController.getString(R.string.DisablePreviewVideoSoundShortcutNotice)));
    private final AbstractConfigCell showTimeHintRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getShowTimeHint()));
    private final AbstractConfigCell disableBotOpenButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getDisableBotOpenButton()));
    private final AbstractConfigCell dividerInteractions = cellGroup.appendCell(new ConfigCellDivider());

    // Sticker
    private final AbstractConfigCell headerSticker = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("StickerSettings", R.string.StickerSettings)));
    private final AbstractConfigCell dontSendGreetingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.dontSendGreetingSticker));
    private final AbstractConfigCell hideTimeForStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.hideTimeForSticker));
    private final AbstractConfigCell stickersOnlyShowReadRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getRealHideTimeForSticker()));
    private final AbstractConfigCell hideGroupStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.hideGroupSticker));
    private final AbstractConfigCell disablePremiumStickerAnimationRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disablePremiumStickerAnimation));
    private final AbstractConfigCell removeFavouriteStickersInRecentStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getRemoveFavouriteStickersInRecentStickers()));
    private final AbstractConfigCell maxRecentStickerCountRow = cellGroup.appendCell(new ConfigCellCustom("MaxRecentStickerCount", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerSticker = cellGroup.appendCell(new ConfigCellDivider());

    // Reaction
    private final AbstractConfigCell headerReaction = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("ReactionSettings", R.string.ReactionSettings)));
//    private final AbstractConfigCell reactionsRow = cellGroup.appendCell(new ConfigCellSelectBox(LocaleController.getString("DoubleTapAndReactions", R.string.doubleTapAndReactions),
//            AxCoreConfig.reactions,
//            new String[]{
//                    LocaleController.getString("doubleTapSendReaction", R.string.doubleTapSendReaction),
//                    LocaleController.getString("doubleTapShowReactionsMenu", R.string.doubleTapShowReactionsMenu),
//                    LocaleController.getString("doubleTapDisable", R.string.doubleTapDisable),
//            }, null));
    private final AbstractConfigCell disableReactionsWhenSelectingRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.disableReactionsWhenSelecting));
    private final AbstractConfigCell dividerReaction = cellGroup.appendCell(new ConfigCellDivider());

    // Operation Confirmatation
    private final AbstractConfigCell headerConfirms = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("ConfirmSettings")));
    private final AbstractConfigCell askBeforeCallRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.askBeforeCall));
    private final AbstractConfigCell skipOpenLinkConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.skipOpenLinkConfirm));
    private final AbstractConfigCell confirmAVRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.confirmAVMessage));
    private final AbstractConfigCell repeatConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(AxCoreConfig.repeatConfirm));
    private final AbstractConfigCell dividerConfirms = cellGroup.appendCell(new ConfigCellDivider());

    // search tag
    private final AbstractConfigCell headerSearchTag = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("SavedTagSearchHint")));
    private final String[] searchPagesString = new String[]{
            LocaleController.getString(R.string.SearchThisChat),
            LocaleController.getString(R.string.SearchMyMessages),
            LocaleController.getString(R.string.SearchPublicPosts),
    };
    private final AbstractConfigCell searchHashtagDefaultPageChannelRow = cellGroup.appendCell(new ConfigCellSelectBox(null, AxConfig.INSTANCE.getSearchHashtagDefaultPageChannel(), searchPagesString, null));
    private final AbstractConfigCell searchHashtagDefaultPageChatRow = cellGroup.appendCell(new ConfigCellSelectBox(null, AxConfig.INSTANCE.getSearchHashtagDefaultPageChat(), searchPagesString, null));
    private final AbstractConfigCell dividerSearchTag  = cellGroup.appendCell(new ConfigCellDivider());

    // Bottom Style
    private final AbstractConfigCell headerBottomStyleTag = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString(R.string.ChatActivityBottomStyle)));
    private final AbstractConfigCell chatActivityNavbarTransparentRow = cellGroup.appendCell(new ConfigCellTextCheck(AxConfig.INSTANCE.getChatActivityNavbarTransparent()));
    private final AbstractConfigCell dividerBottomStyleTag  = cellGroup.appendCell(new ConfigCellDivider());

    private ActionBarMenuItem menuItem;
    private StickerSizeCell stickerSizeCell;
    private EmojiSetCell emojiSetCell;

    public AxChatSettingsActivity() {
        updateRows();
    }

    @Override
    public boolean onFragmentCreate() {
        EmojiHelper.getInstance().loadEmojisInfo(this);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        super.onFragmentCreate();

//        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
        updateRows();

        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        var superView = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuItem.addSubItem(1, R.drawable.msg_reset, LocaleController.getString("ResetStickerSize", R.string.ResetStickerSize));
        menuItem.setVisibility(AxCoreConfig.stickerSize.Float() != 14.0f ? View.VISIBLE : View.GONE);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    AxCoreConfig.stickerSize.setConfigFloat(14.0f);
                    menuItem.setVisibility(View.GONE);
                    stickerSizeCell.invalidate();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof one.axgram.core.config.cell.ConfigCellText) {
                ((one.axgram.core.config.cell.ConfigCellText) a).onClick();
            } else if (a instanceof ConfigCellTextDetail) {
                RecyclerListView.OnItemClickListener o = ((ConfigCellTextDetail) a).onItemClickListener;
                if (o != null) {
                    try {
                        o.onItemClick(view, position);
                    } catch (
                            Exception e) {
                    }
                }
            } else if (a instanceof ConfigCellCustom) { // Custom onclick
                if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
                    final int[] counts = {20, 30, 40, 50, 80, 100, 120, 150, 180, 200};
                    List<String> types = Arrays.stream(counts)
                            .filter(i -> i <= getMessagesController().maxRecentStickersCount)
                            .mapToObj(String::valueOf)
                            .collect(Collectors.toList());
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(types, (i, str) -> {
                        AxCoreConfig.maxRecentStickerCount.setConfigInt(Integer.parseInt(str.toString()));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                } else if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    ArrayList<Integer> types = new ArrayList<>();
                    arrayList.add(LocaleController.getString("Disable", R.string.Disable));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_NONE);
                    arrayList.add(LocaleController.getString("SendReactions", R.string.SendReactions));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS);
                    arrayList.add(LocaleController.getString("ShowReactions", R.string.ShowReactions));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SHOW_REACTIONS);
                    arrayList.add(LocaleController.getString("TranslateMessage", R.string.TranslateMessage));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE);
                    arrayList.add(LocaleController.getString("Reply", R.string.Reply));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPLY);
                    arrayList.add(LocaleController.getString("AddToSavedMessages", R.string.AddToSavedMessages));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SAVE);
                    arrayList.add(LocaleController.getString("Repeat", R.string.Repeat));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT);
                    arrayList.add(LocaleController.getString("RepeatAsCopy", R.string.RepeatAsCopy));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT_AS_COPY);
                    arrayList.add(LocaleController.getString("Edit", R.string.Edit));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_EDIT);
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(arrayList, (i, str) -> {
                        AxConfig.INSTANCE.getDoubleTapAction().setConfigInt(types.get(i));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                } else if (position == cellGroup.rows.indexOf(emojiSetsRow)) {
                    presentFragment(new AxEmojiSettingsActivity());
                }
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, AxChatSettingsActivity.this, "chat", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(AxCoreConfig.hideAllTab.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            } else if (key.equals(AxCoreConfig.tabsTitleType.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            } else if (key.equals(AxCoreConfig.disableProximityEvents.getKey())) {
                MediaController.getInstance().recreateProximityWakeLock();
            } else if (key.equals(AxCoreConfig.showSeconds.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESATRT, null, null);
            } else if (key.equals(AxConfig.INSTANCE.getDisableBotOpenButton().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESATRT, null, null);
            }
        };

        //Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        return superView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getDrawable() {
        return R.drawable.menu_chats;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString("Chat", R.string.Chat);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }

    public static AlertDialog showConfigMenuAlert(Context context, String titleKey, ArrayList<ConfigCellTextCheck> configItems) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(titleKey));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
        linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int count = configItems.size();
        for (int a = 0; a < count; a++) {
            ConfigCellTextCheck configItem = configItems.get(a);
            TextCheckCell textCell = new TextCheckCell(context);
            textCell.setTextAndCheck(configItem.getTitle(), configItem.getBindConfig().Bool(), false);
            textCell.setTag(a);
            textCell.setBackground(Theme.getSelectorDrawable(false));
            linearLayoutInviteContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            int finalA = a;
            textCell.setOnClickListener(v2 -> {
                Integer tag = (Integer) v2.getTag();
                if (tag == finalA) {
                    textCell.setChecked(configItem.getBindConfig().toggleConfigBool());
                }
            });
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setView(linearLayout);
        return builder.create();
    }

    public static boolean[] getDeleteMenuChecks() {
        return new boolean[]{
                AxConfig.INSTANCE.getDefaultDeleteMenuBanUsers().Bool(),
                AxConfig.INSTANCE.getDefaultDeleteMenReportSpam().Bool(),
                AxConfig.INSTANCE.getDefaultDeleteMenuDeleteAll().Bool(),
                AxConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups().Bool(),
                AxConfig.INSTANCE.getDefaultDeleteMenuDeleteAllReactions().Bool(),
        };
    }

    @Override
    public void emojiPacksLoaded(String error) {
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow), PARTIAL);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow), PARTIAL);
        }
       /* if (id == NotificationCenter.emojiDidLoad) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }*/
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
//        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
    }

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 2;
        private final int endStickerSize = 20;

        private final TextPaint textPaint;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    AxCoreConfig.stickerSize.setConfigFloat(startStickerSize + (endStickerSize - startStickerSize) * progress);
                    StickerSizeCell.this.invalidate();
                    menuItem.setVisibility(View.VISIBLE);
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {

                }
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewMessagesCell(context, parentLayout);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + Math.round(AxCoreConfig.stickerSize.Float()), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((AxCoreConfig.stickerSize.Float() - startStickerSize) / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }
    }

    //impl ListAdapter
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial, boolean divider) {
            View view = holder.itemView;
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    // Custom binds
                    if (holder.itemView instanceof TextSettingsCell) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
                            textCell.setTextAndValue(LocaleController.getString(R.string.maxRecentStickerCount), String.valueOf(AxCoreConfig.maxRecentStickerCount.Int()), divider);
                        } else if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                            textCell.setTextAndValue(LocaleController.getString(R.string.DoubleTapAction), DoubleTap.doubleTapActionMap.get(AxConfig.INSTANCE.getDoubleTapAction().Int()), divider);
                        }
                    } else if (view instanceof EmojiSetCell) {
                        EmojiSetCell v1 =  (EmojiSetCell) view;
                        v1.setData(EmojiHelper.getInstance().getCurrentEmojiPackInfo(), false, divider);
                    }
                } else {
                    // Default binds
                    a.onBindViewHolder(holder);
                }
            }
        }

        @Override
        public View onCreateViewHolderView(int viewType) {
            View view = null;
            switch (viewType) {
                case ConfigCellCustom.CUSTOM_ITEM_StickerSize:
                    view = stickerSizeCell = new StickerSizeCell(mContext);
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_EmojiSet:
                    view = emojiSetCell = new EmojiSetCell(mContext, false);
                    break;
            }
            if (view != null) {
                return view;
            }
            return super.onCreateViewHolderView(viewType);
        }
    }

    @Override
    protected void setCanNotChange() {
        super.setCanNotChange();

        if (!AxCoreConfig.showRepeat.Bool() || AxConfig.INSTANCE.getShowRepeatAsCopy().Bool()){
            cellGroup.rows.remove(autoReplaceRepeatRow);
            AxConfig.INSTANCE.getAutoReplaceRepeat().setConfigBool(false);
        }

        addRowsToMap();
    }

    private static final int MODE_HIDE = 0;
    private static final int MODE_TEXT = 1;
    private static final int MODE_ICON = 2;

    private void showMessageMenuAlert() {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MessageMenu));

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0));

        // Order matches the typical fillMessageMenu sequence in ChatActivity.
        addHideCompactRow(container, LocaleController.getString(R.string.Reply), new int[]{org.telegram.ui.ChatActivity.OPTION_REPLY});
        addHideCompactRow(container, LocaleController.getString(R.string.Copy), new int[]{org.telegram.ui.ChatActivity.OPTION_COPY});
        addHideCompactRow(container, LocaleController.getString(R.string.ViewThread), new int[]{org.telegram.ui.ChatActivity.OPTION_VIEW_REPLIES_OR_THREAD});
        addHideCompactRow(container, LocaleController.getString(R.string.CopyLink), new int[]{
                org.telegram.ui.ChatActivity.OPTION_COPY_LINK,
                org.telegram.ui.ChatActivity.nkbtn_copy_link_in_pm,
        });
        addHideCompactRow(container, LocaleController.getString(R.string.ViewInTopic), new int[]{org.telegram.ui.ChatActivity.OPTION_VIEW_IN_TOPIC});
        addHideCompactRow(container, LocaleController.getString(R.string.SaveToDownloads), new int[]{org.telegram.ui.ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC});
        addHideCompactRow(container, LocaleController.getString(R.string.SaveToGIFs), new int[]{org.telegram.ui.ChatActivity.OPTION_ADD_TO_GIFS});
        addShowCompactRow(container, R.string.DeleteDownloadedFile, () -> AxCoreConfig.showDeleteDownloadedFile.Bool(), v -> AxCoreConfig.showDeleteDownloadedFile.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_deldlcache);
        addHideCompactRow(container, LocaleController.getString(R.string.SaveToGallery), new int[]{
                org.telegram.ui.ChatActivity.OPTION_SAVE_TO_GALLERY,
                org.telegram.ui.ChatActivity.OPTION_SAVE_TO_GALLERY2,
                org.telegram.ui.ChatActivity.nkbtn_stickerdl,
        });
        addShowCompactRow(container, R.string.CopyPhoto, () -> AxConfig.INSTANCE.getShowCopyPhoto().Bool(), v -> AxConfig.INSTANCE.getShowCopyPhoto().setConfigBool(v), new int[]{
                org.telegram.ui.ChatActivity.OPTION_COPY_PHOTO,
                org.telegram.ui.ChatActivity.OPTION_COPY_PHOTO_AS_STICKER,
                org.telegram.ui.ChatActivity.nkbtn_sticker_copy,
                org.telegram.ui.ChatActivity.nkbtn_sticker_copy_png,
        });
        addHideCompactRow(container, LocaleController.getString(R.string.AddToStickers), new int[]{org.telegram.ui.ChatActivity.OPTION_ADD_TO_STICKERS_OR_MASKS});
        addHideCompactRow(container, LocaleController.getString(R.string.AddToFavorites), new int[]{org.telegram.ui.ChatActivity.OPTION_ADD_STICKER_TO_FAVORITES});
        addHideCompactRow(container, LocaleController.getString(R.string.DeleteFromFavorites), new int[]{org.telegram.ui.ChatActivity.OPTION_DELETE_STICKER_FROM_FAVORITES});
        addHideCompactRow(container, LocaleController.getString(R.string.Forward), new int[]{org.telegram.ui.ChatActivity.OPTION_FORWARD});
        addShowCompactRow(container, R.string.NoQuoteForward, () -> AxConfig.INSTANCE.getShowNoQuoteForward().Bool(), v -> AxConfig.INSTANCE.getShowNoQuoteForward().setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_forward_noquote);
        addShowCompactRow(container, R.string.AddToSavedMessages, () -> AxCoreConfig.showAddToSavedMessages.Bool(), v -> AxCoreConfig.showAddToSavedMessages.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_savemessage);
        addShowCompactRow(container, R.string.Repeat, () -> AxCoreConfig.showRepeat.Bool(), v -> AxCoreConfig.showRepeat.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_repeat);
        addShowCompactRow(container, R.string.RepeatAsCopy, () -> AxConfig.INSTANCE.getShowRepeatAsCopy().Bool(), v -> AxConfig.INSTANCE.getShowRepeatAsCopy().setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_repeatascopy);
        addShowCompactRow(container, R.string.InvertReply, () -> AxConfig.INSTANCE.getShowInvertReply().Bool(), v -> AxConfig.INSTANCE.getShowInvertReply().setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_invertReply);
        addHideCompactRow(container, LocaleController.getString(R.string.CustomGreat), new int[]{org.telegram.ui.ChatActivity.nkbtn_greatOrPoor});
        addShowCompactRow(container, R.string.ViewHistory, () -> AxCoreConfig.showViewHistory.Bool(), v -> AxCoreConfig.showViewHistory.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_view_history);
        addShowCompactRow(container, R.string.MessageDetails, () -> AxCoreConfig.showMessageDetails.Bool(), v -> AxCoreConfig.showMessageDetails.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_detail);
        addHideCompactRow(container, LocaleController.getString(R.string.Statistics), new int[]{org.telegram.ui.ChatActivity.OPTION_STATISTICS});
        addHideCompactRow(container, LocaleController.getString(R.string.PinMessage), new int[]{org.telegram.ui.ChatActivity.OPTION_PIN});
        addHideCompactRow(container, LocaleController.getString(R.string.UnpinMessage), new int[]{org.telegram.ui.ChatActivity.OPTION_UNPIN});
        addShowCompactRow(container, R.string.TranslateMessage, () -> AxCoreConfig.showTranslate.Bool(), v -> AxCoreConfig.showTranslate.setConfigBool(v), new int[]{
                org.telegram.ui.ChatActivity.OPTION_TRANSLATE,
                org.telegram.ui.ChatActivity.nkbtn_translate,
        });
        addHideCompactRow(container, LocaleController.getString(R.string.Edit), new int[]{org.telegram.ui.ChatActivity.OPTION_EDIT});
        addShowCompactRow(container, R.string.ReportChat, () -> AxCoreConfig.showReport.Bool(), v -> AxCoreConfig.showReport.setConfigBool(v), org.telegram.ui.ChatActivity.OPTION_REPORT_CHAT);
        addShowCompactRow(container, R.string.ShareMessages, () -> AxCoreConfig.showShareMessages.Bool(), v -> AxCoreConfig.showShareMessages.setConfigBool(v), new int[]{
                org.telegram.ui.ChatActivity.OPTION_SHARE,
                org.telegram.ui.ChatActivity.nkbtn_sharemessage,
        });
        addShowCompactRow(container, R.string.EditAdminRights, () -> AxCoreConfig.showAdminActions.Bool(), v -> AxCoreConfig.showAdminActions.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_editAdmin);
        addShowCompactRow(container, R.string.ChangePermissions, () -> AxCoreConfig.showChangePermissions.Bool(), v -> AxCoreConfig.showChangePermissions.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_editPermission);
        addShowCompactRow(container, R.string.Hide, () -> AxCoreConfig.showMessageHide.Bool(), v -> AxCoreConfig.showMessageHide.setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_hide);
        addShowCompactRow(container, R.string.SetReminder, () -> AxConfig.INSTANCE.getShowSetReminder().Bool(), v -> AxConfig.INSTANCE.getShowSetReminder().setConfigBool(v), org.telegram.ui.ChatActivity.nkbtn_setReminder);
        addHideCompactRow(container, LocaleController.getString(R.string.OpenProfile), new int[]{org.telegram.ui.ChatActivity.OPTION_OPEN_PROFILE});
        addHideCompactRow(container, LocaleController.getString(R.string.Delete), new int[]{org.telegram.ui.ChatActivity.OPTION_DELETE});
        addShowOnlyRow(container, R.string.Reactions, () -> AxConfig.INSTANCE.getShowReactions().Bool(), v -> AxConfig.INSTANCE.getShowReactions().setConfigBool(v));

        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.setView(scroll);
        showDialog(builder.create());
    }

    private void addShowCompactRow(LinearLayout parent, int labelRes,
                                   java.util.function.BooleanSupplier showGetter,
                                   java.util.function.Consumer<Boolean> showSetter,
                                   int option) {
        addShowCompactRow(parent, labelRes, showGetter, showSetter, new int[]{option});
    }

    private void addShowCompactRow(LinearLayout parent, int labelRes,
                                   java.util.function.BooleanSupplier showGetter,
                                   java.util.function.Consumer<Boolean> showSetter,
                                   int[] options) {
        // 1. 检查该选项是否允许compact（图标）模式
        boolean compactAllowed = one.axgram.extra.helper.MessageMenuCompact.isAllowed(options[0]);

        // 2. enabled数组控制三个段按钮的可用性
        // [HIDE按钮, TEXT按钮, ICON按钮]
        boolean[] enabled = new boolean[]{true, true, compactAllowed};

        // 3. getter函数：获取当前模式
        java.util.function.IntSupplier getter = () -> {
            // 如果不显示，返回HIDE模式
            if (!showGetter.getAsBoolean()) return MODE_HIDE;
            // 否则检查是否compact模式
            return one.axgram.extra.helper.MessageMenuCompact.isCompact(options[0])
                    ? MODE_ICON : MODE_TEXT;
        };

        // 4. setter函数：设置模式
        java.util.function.IntConsumer setter = mode -> {
            if (mode == MODE_HIDE) {
                // 切换到HIDE模式时，如果当前是显示的，则关闭显示
                if (showGetter.getAsBoolean()) showSetter.accept(false);
            } else {
                // 切换到TEXT或ICON模式时，如果当前是隐藏的，则开启显示
                if (!showGetter.getAsBoolean()) showSetter.accept(true);
                // 设置compact模式（true=ICON, false=TEXT）
                one.axgram.extra.helper.MessageMenuCompact.setCompact(options, mode == MODE_ICON);
            }
        };

        // 5. 调用底层方法添加三态行
        addThreeStateRow(parent, LocaleController.getString(labelRes), getter, setter, enabled);
    }

    private void addShowOnlyRow(LinearLayout parent, int labelRes,
                                java.util.function.BooleanSupplier showGetter,
                                java.util.function.Consumer<Boolean> showSetter) {
        boolean[] enabled = new boolean[]{true, true, false};
        java.util.function.IntSupplier getter = () -> showGetter.getAsBoolean() ? MODE_TEXT : MODE_HIDE;
        java.util.function.IntConsumer setter = mode -> {
            if (mode == MODE_HIDE) showSetter.accept(false);
            else if (mode == MODE_TEXT) showSetter.accept(true);
        };
        addThreeStateRow(parent, LocaleController.getString(labelRes), getter, setter, enabled);
    }

    /** HIDE / TEXT only (no ICON segment) for items where compact mode isn't safe. */
    private void addHideTextRow(LinearLayout parent, String label, int[] options) {
        boolean[] enabled = new boolean[]{true, true, false};
        java.util.function.IntSupplier getter = () -> one.axgram.extra.helper.MessageMenuCompact.isHidden(options[0]) ? MODE_HIDE : MODE_TEXT;
        java.util.function.IntConsumer setter = mode -> one.axgram.extra.helper.MessageMenuCompact.setHidden(options, mode == MODE_HIDE);
        addThreeStateRow(parent, label, getter, setter, enabled);
    }

    private void addHideCompactRow(LinearLayout parent, String label, int[] options) {
        boolean compactAllowed = one.axgram.extra.helper.MessageMenuCompact.isAllowed(options[0]);
        boolean[] enabled = new boolean[]{true, true, compactAllowed};
        java.util.function.IntSupplier getter = () -> {
            if (one.axgram.extra.helper.MessageMenuCompact.isHidden(options[0])) return MODE_HIDE;
            return one.axgram.extra.helper.MessageMenuCompact.isCompact(options[0]) ? MODE_ICON : MODE_TEXT;
        };
        java.util.function.IntConsumer setter = mode -> {
            if (mode == MODE_HIDE) {
                one.axgram.extra.helper.MessageMenuCompact.setHidden(options, true);
                one.axgram.extra.helper.MessageMenuCompact.setCompact(options, false);
            } else {
                one.axgram.extra.helper.MessageMenuCompact.setHidden(options, false);
                one.axgram.extra.helper.MessageMenuCompact.setCompact(options, mode == MODE_ICON);
            }
        };
        addThreeStateRow(parent, label, getter, setter, enabled);
    }

    private void addThreeStateRow(LinearLayout parent, String label,
                                  java.util.function.IntSupplier getMode,
                                  java.util.function.IntConsumer setMode,
                                  boolean[] enabled) {
        Context ctx = getParentActivity();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = AndroidUtilities.dp(20);
        int padV = AndroidUtilities.dp(8);
        row.setPadding(padH, padV, padH, padV);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        String[] segLabels = new String[]{
                LocaleController.getString(R.string.SegHide),
                LocaleController.getString(R.string.SegText),
                LocaleController.getString(R.string.SegIcon),
        };
        one.axgram.extra.ui.SegmentedThreeStateView seg = new one.axgram.extra.ui.SegmentedThreeStateView(ctx, segLabels, enabled);
        seg.setSelection(getMode.getAsInt(), false);
        seg.setOnChange(setMode);
        row.addView(seg, LayoutHelper.createLinear(180, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
