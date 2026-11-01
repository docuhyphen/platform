import React from 'react';
import {
    InteractionTag,
    InteractionTagPrimary,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    TagGroup,
} from '@fluentui/react-components';

interface Props {
    tags: string[];
    max?: number;
}

const TagList: React.FC<Props> = ({tags, max = 3}) => {
    if (!tags.length) return null;
    const visible = tags.slice(0, max);
    const hidden = tags.slice(max);
    return (
        <TagGroup id={"tag-list-tag-group-1"}>
            {visible.map(tag => (
                <InteractionTag id={`tag-list-interaction-tag-1-${tag}`}
                                key={tag}
                                size="small"
                                shape="circular">
                    <InteractionTagPrimary id={"tag-list-interaction-tag-primary-1"}
                                           hasSecondaryAction={false}>{tag}</InteractionTagPrimary>
                </InteractionTag>
            ))}
            {hidden.length > 0 && (
                <Menu>
                    <MenuTrigger disableButtonEnhancement>
                        <InteractionTag id={"tag-list-interaction-tag-2"}
                                        size="small"
                                        shape="circular">
                            <InteractionTagPrimary id={"tag-list-interaction-tag-primary-2"}>+{hidden.length}</InteractionTagPrimary>
                        </InteractionTag>
                    </MenuTrigger>
                    <MenuPopover id={"tag-list-menu-popover-1"}>
                        <MenuList id={"tag-list-menu-list-1"}>
                            {hidden.map(tag => (
                                <MenuItem id={`tag-list-menu-item-1-${tag}`}
                                          key={tag}>{tag}</MenuItem>
                            ))}
                        </MenuList>
                    </MenuPopover>
                </Menu>
            )}
        </TagGroup>
    );
};

export default TagList;
