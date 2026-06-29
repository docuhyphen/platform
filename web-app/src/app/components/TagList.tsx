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
        <TagGroup>
            {visible.map(tag => (
                <InteractionTag key={tag} size="small" shape="circular">
                    <InteractionTagPrimary hasSecondaryAction={false}>{tag}</InteractionTagPrimary>
                </InteractionTag>
            ))}
            {hidden.length > 0 && (
                <Menu>
                    <MenuTrigger disableButtonEnhancement>
                        <InteractionTag size="small" shape="circular">
                            <InteractionTagPrimary>+{hidden.length}</InteractionTagPrimary>
                        </InteractionTag>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList>
                            {hidden.map(tag => (
                                <MenuItem key={tag}>{tag}</MenuItem>
                            ))}
                        </MenuList>
                    </MenuPopover>
                </Menu>
            )}
        </TagGroup>
    );
};

export default TagList;
