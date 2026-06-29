import React from "react";
import {Dropdown, Option, OptionOnSelectData, SelectionEvents, Tag, Text} from "@fluentui/react-components";
import {NotificationPreferenceChannel} from "../../../models/models.tsx";
import {useNotificationPreferenceRowStyles} from "./NotificationPreferenceRowStyles.tsx";

interface NotificationPreferenceRowProps {
    id: string;
    title: string;
    description: string;
    channels: NotificationPreferenceChannel[];
    disabled: boolean;
    onChange: (channels: NotificationPreferenceChannel[]) => void;
}

const channelLabels: Record<NotificationPreferenceChannel, string> = {
    EMAIL: "Email",
    IN_APP: "Push",
};

const NotificationPreferenceRow: React.FC<NotificationPreferenceRowProps> = (props) => {
    const styles = useNotificationPreferenceRowStyles();
    const onOptionSelect = (_event: SelectionEvents, data: OptionOnSelectData) => {
        props.onChange(data.selectedOptions as NotificationPreferenceChannel[]);
    };
    const selectedChannelTags = (
        <div id={`notification-channels-selected-${props.id}`}
             className={styles.selectedChannels}>
            {props.channels.length === 0 ? (
                <Text size={200}
                      className={styles.emptySelection}>
                    None
                </Text>
            ) : props.channels.map(channel => (
                <Tag key={channel}
                     id={`notification-channel-tag-${props.id}-${channel.toLowerCase()}`}
                     size="small">
                    {channelLabels[channel]}
                </Tag>
            ))}
        </div>
    );

    return (
        <section id={`notification-preference-${props.id}`}
                 className={styles.container}>
            <div id={`notification-preference-top-${props.id}`}
                 className={styles.topRow}>
                <Text id={`notification-preference-title-${props.id}`}
                      className={styles.title}
                      weight="semibold">
                    {props.title}
                </Text>
                <Dropdown id={`notification-channels-${props.id}`}
                          className={styles.dropdown}
                          aria-label={`Delivery channels for ${props.title}`}
                          multiselect
                          appearance={"outline"}
                          disabled={props.disabled}
                          selectedOptions={props.channels}
                          onOptionSelect={onOptionSelect}
                          button={{children: selectedChannelTags}}>
                    <Option id={`notification-channel-email-${props.id}`}
                            value="EMAIL"
                            text="Email">
                        Email
                    </Option>
                    <Option id={`notification-channel-push-${props.id}`}
                            value="IN_APP"
                            text="Push">
                        Push
                    </Option>
                </Dropdown>
            </div>
            <Text id={`notification-preference-description-${props.id}`}
                  className={styles.description}
                  size={200}>
                {props.description}
            </Text>
        </section>
    );
};

export default NotificationPreferenceRow;
