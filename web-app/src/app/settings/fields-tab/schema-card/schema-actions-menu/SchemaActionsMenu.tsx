import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {AddRegular, BranchRegular, DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {SchemaDefinitionDto} from '../../../../models/models';

interface Props
{
    schema: SchemaDefinitionDto;
    onEdit: (schema: SchemaDefinitionDto) => void;
    onPublish: (schema: SchemaDefinitionDto) => void;
    onNewVersion: (schema: SchemaDefinitionDto) => void;
    onRetire: (schema: SchemaDefinitionDto) => void;
}

const SchemaActionsMenu = ({schema, onEdit, onPublish, onNewVersion, onRetire}: Props) =>
    (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button id={`schema-menu-${schema.id}`}
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<MoreVerticalRegular/>}/>
            </MenuTrigger>
            <MenuPopover id={"schema-card-menu-popover-1"}>
                <MenuList id={"schema-card-menu-list-1"}>
                    {schema.draftVersion && (
                        <MenuItem id={"schema-card-menu-item-1"}
                                  icon={<EditRegular/>}
                                  onClick={() => onEdit(schema)}>
                            Edit fields
                        </MenuItem>
                    )}
                    {schema.draftVersion && (
                        <MenuItem id={"schema-card-menu-item-2"}
                                  icon={<BranchRegular/>}
                                  onClick={() => onPublish(schema)}>
                            Publish draft
                        </MenuItem>
                    )}
                    {!schema.draftVersion && (
                        <MenuItem id={"schema-card-menu-item-3"}
                                  icon={<AddRegular/>}
                                  onClick={() => onNewVersion(schema)}>
                            New version
                        </MenuItem>
                    )}
                    <MenuItem id={"schema-card-menu-item-4"}
                              icon={<DeleteRegular/>}
                              onClick={() => onRetire(schema)}>
                        Retire
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );

export default SchemaActionsMenu;
