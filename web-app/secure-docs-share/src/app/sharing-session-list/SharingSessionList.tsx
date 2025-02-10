import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/api.ts";
import {
    Avatar,
    Button,
    Field,
    List,
    ListItem,
    makeStyles,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SearchBox,
    Spinner,
    Tooltip,
    typographyStyles
} from "@fluentui/react-components";
import "./SharingSessionList.css"
import {
    ArrowSortDownLinesFilled,
    ArrowSortDownLinesRegular,
    ArrowSortUpLinesFilled,
    ArrowSortUpLinesRegular,
    bundleIcon,
    FilterFilled,
    FilterRegular
} from "@fluentui/react-icons";
import {formatDate} from "../helpers.ts";
import {SharingSessionBasicDto} from "../models/models.tsx";

const useStyles = makeStyles({
    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,
});

interface SharingSessionListProps
{
    onSelectionChange: (sessionId: string) => void;
}

const SharingSessionList: React.FC<SharingSessionListProps> = ({onSelectionChange}) =>
{
    const styles = useStyles();
    const token = useToken()
    const [sharingSessions, setSharingSessions] = useState<SharingSessionBasicDto[]>([]);
    const [loadingSharingSessions, setLoadingSharingSessions] = useState(true);
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const fetchSharingSessions = async () =>
    {
        try
        {
            const response = await fetchSignedInUserAppUserSharingSessions(token);

            if (Array.isArray(response) && response.length)
            {
                setSharingSessions(response);
                setSelectedItems([response[0].id]);
                onSelectionChange(response[0].id);
            }
            else
            {
                console.error("Error fetching sharing sessions:", response);
            }
        }
        catch (error)
        {
            console.error(error);
        }
        finally
        {
            setLoadingSharingSessions(false);
        }
    }

    useEffect(() =>
    {
        fetchSharingSessions()
    }, [])

    const handleSelectionChange = (_, data) =>
    {
        setSelectedItems(data.selectedItems);
        onSelectionChange(data.selectedItems[0]);
    };

    const FilterIcon = bundleIcon(FilterFilled, FilterRegular);
    const SortDownIcon = bundleIcon(ArrowSortDownLinesFilled, ArrowSortDownLinesRegular);
    const SortUpIcon = bundleIcon(ArrowSortUpLinesFilled, ArrowSortUpLinesRegular);

    const onFocus = React.useCallback((event) =>
    {
        if (event.target !== event.currentTarget)
        {
            return;
        }
    }, []);

    const listItemCard = (session: any) =>
    {
        return <div className={"list-card"}>
            <section className={"list-card-item"}>
                <span>
                    <Avatar name={session.recipientEmail}/>
                </span>
                <span className={"list-card-item-details"}>
                    <div className={styles.caption1}>{session.recipientEmail}</div>
                    <div className={"list-card-item-row"}>
                        <div className={styles.body1Strong}>{session.sessionName}</div>
                        <div className={styles.caption2}>{formatDate(session.createdDate)}</div>
                    </div>
                    <div>
                        <div className={styles.caption1}> {session.description} </div>
                    </div>
                </span>
            </section>
        </div>
    }

    return (
        <section id={"sharing-sessions-list-container"}>
            <List
                id={"sharing-sessions-list-body"}
                selectionMode="single"
                navigationMode="composite"
                selectedItems={selectedItems}
                onSelectionChange={handleSelectionChange}
            >
                {sharingSessions.map((session: any) => (
                    <>
                        <ListItem
                            className={selectedItems.includes(session.id) ? "sharing-sessions-list-selected-item" : ""}
                            key={session.id}
                            value={session.id}
                            data-value={session.id}
                            onFocus={onFocus}
                            checkmark={null}
                        >
                            {listItemCard(session)}
                        </ListItem>
                    </>
                ))}
            </List>
            <div id={"sharing-sessions-list-header"}>
                <Field id={"filter-search-field"}>
                    <SearchBox/>
                </Field>
                <Menu>
                    <MenuTrigger>
                        <Tooltip content="Filter sharing sessions" relationship={"description"}>
                            <Button icon={<FilterIcon/>} appearance={"subtle"}/>
                        </Tooltip>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList>
                            <MenuItem icon={<FilterIcon/>}>
                                Cut
                            </MenuItem>
                            <MenuItem icon={<FilterIcon/>}>
                                Edit
                            </MenuItem>
                        </MenuList>
                    </MenuPopover>
                </Menu>
                <Tooltip content="Sort descending" relationship={"description"}>
                    <Button icon={<SortDownIcon/>} appearance={"subtle"}/>
                </Tooltip>
            </div>
            <div id={"sharing-sessions-list-footer"}>
                <span>
                    {
                        loadingSharingSessions &&
                        <Spinner size={"extra-small"}/>
                    }
                    {!loadingSharingSessions &&
                        <>
                            Showing <strong> {sharingSessions.length} </strong> Sharing Sessions
                        </>
                    }
                </span>
                <Button size={"small"} appearance={"primary"} disabled>View All</Button>
            </div>
        </section>
);
}

export default SharingSessionList;