import {AppUserSettingsDto} from "../../models/models.tsx";

export type NotificationChannelSettingKey =
    | "notifyShareStartChannels"
    | "notifyShareAcceptChannels"
    | "notifyShareDeclineChannels"
    | "notifyShareEndChannels"
    | "notifyDocCommentChannels"
    | "notifyDocDeleteChannels"
    | "notifyDocAddChannels"
    | "notifyDocUploadChannels";

export type LegacyNotificationSettingKey =
    | "notifyShareStart"
    | "notifyShareAccept"
    | "notifyShareDecline"
    | "notifyShareEnd"
    | "notifyDocComment"
    | "notifyDocDelete"
    | "notifyDocAdd"
    | "notifyDocUpload";

export interface NotificationPreferenceDefinition {
    id: string;
    title: string;
    description: string;
    channelSetting: NotificationChannelSettingKey;
    legacySetting: LegacyNotificationSettingKey;
}

export const notificationPreferenceDefinitions: NotificationPreferenceDefinition[] = [
    {
        id: "exchange-initiated",
        title: "Exchange initiated",
        description: "When an Exchange is sent to you, or when an Exchange you initiate is sent to its recipients.",
        channelSetting: "notifyShareStartChannels",
        legacySetting: "notifyShareStart",
    },
    {
        id: "exchange-accepted",
        title: "Exchange accepted",
        description: "When a recipient accepts an Exchange that you initiated.",
        channelSetting: "notifyShareAcceptChannels",
        legacySetting: "notifyShareAccept",
    },
    {
        id: "exchange-declined",
        title: "Exchange declined",
        description: "When a recipient declines an Exchange that you initiated.",
        channelSetting: "notifyShareDeclineChannels",
        legacySetting: "notifyShareDecline",
    },
    {
        id: "exchange-ended",
        title: "Exchange ended",
        description: "When an Exchange you participate in is ended and moves to its completed state.",
        channelSetting: "notifyShareEndChannels",
        legacySetting: "notifyShareEnd",
    },
    {
        id: "document-comments",
        title: "Document notes and comments",
        description: "When someone adds a note or comment to a document in an Exchange you can access.",
        channelSetting: "notifyDocCommentChannels",
        legacySetting: "notifyDocComment",
    },
    {
        id: "document-deleted",
        title: "Document deleted",
        description: "When a document is removed from an Exchange you participate in.",
        channelSetting: "notifyDocDeleteChannels",
        legacySetting: "notifyDocDelete",
    },
    {
        id: "document-added",
        title: "Document added",
        description: "When a new document is added to an Exchange you participate in.",
        channelSetting: "notifyDocAddChannels",
        legacySetting: "notifyDocAdd",
    },
    {
        id: "document-uploaded",
        title: "Document uploaded",
        description: "When a file or a new file version is uploaded to a document in an Exchange you participate in.",
        channelSetting: "notifyDocUploadChannels",
        legacySetting: "notifyDocUpload",
    },
];

export const getNotificationChannels = (
    settings: AppUserSettingsDto,
    definition: NotificationPreferenceDefinition,
) => settings[definition.channelSetting] ??
    (settings[definition.legacySetting] ? ["EMAIL", "IN_APP"] : []);
