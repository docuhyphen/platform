import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/sharingSessionApi.ts";
import {
    Avatar,
    Button,
    Divider,
    Field,
    List,
    ListItem,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SearchBox,
    SkeletonItem,
    Spinner,
    Tooltip
} from "@fluentui/react-components";
import {
    ArrowSortDownLinesFilled,
    ArrowSortDownLinesRegular,
    bundleIcon,
    FilterFilled,
    FilterRegular
} from "@fluentui/react-icons";
import {formatDateWithOrdinal} from "../helpers.ts";
import {SharingSessionBasicDto} from "../models/models.tsx";
import {useSharingSessionStyles} from "./SharingSessionListStyles.tsx";
import {
    sharingSessionDeletionObservable,
    sharingSessionInitiationObservable, sharingSessionUpdatedObservable
} from "../observable/sharingSessionObservables.ts";

interface SharingSessionListProps {
    onSelectionChange: (sessionId: string) => void;
}

const SharingSessionList: React.FC<SharingSessionListProps> = ({onSelectionChange}) => {
    const styles = useSharingSessionStyles();
    const token = useToken();
    const [sharingSessions, setSharingSessions] = useState<SharingSessionBasicDto[]>([]);
    const [loadingSharingSessions, setLoadingSharingSessions] = useState(true);
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const FilterIcon = bundleIcon(FilterFilled, FilterRegular);
    const SortDownIcon = bundleIcon(ArrowSortDownLinesFilled, ArrowSortDownLinesRegular);

    const fetchSharingSessions = async () => {
        try {
            const response = await fetchSignedInUserAppUserSharingSessions(token);

            if (Array.isArray(response) && response.length) {
                setSharingSessions(response);

                const urlParams = new URLSearchParams(window.location.search);
                const sessionId = urlParams.get('s');

                if (sessionId) {
                    const sessionExists = response.some(session => session.id === sessionId);
                    if (sessionExists) {
                        setSelectedItems([sessionId]);
                        onSelectionChange(sessionId);
                    } else {
                        setSelectedItems([response[0].id]);
                        onSelectionChange(response[0].id);
                    }
                } else {
                    setSelectedItems([response[0].id]);
                    onSelectionChange(response[0].id);
                }
            } else {
                console.error("Error fetching sharing sessions:", response);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoadingSharingSessions(false);
        }
    };

    useEffect(() => {
        fetchSharingSessions();
    }, []);

    useEffect(() => {
        const initiationSubscription = sharingSessionInitiationObservable.subscribe(session => {
            if (session) {
                setSharingSessions(prevSessions => [session, ...prevSessions]);
                setSelectedItems([session.id]);
                onSelectionChange(session.id);
            }
        });

        const deletionSubscription = sharingSessionDeletionObservable.subscribe(sessionId => {
            setSharingSessions(prevSessions => prevSessions.filter(session => session.id !== sessionId));
        });

        const updatedSubscription = sharingSessionUpdatedObservable.subscribe(updatedSession => {


        });

        return () => {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
            updatedSubscription
        };
    }, []);

    const handleSelectionChange = (_, data) => {
        setSelectedItems(data.selectedItems);
        onSelectionChange(data.selectedItems[0]);

        const urlParams = new URLSearchParams(window.location.search);
        urlParams.set('s', data.selectedItems[0]);
        window.history.replaceState(null, '', `?${urlParams.toString()}`);
    };

    const onListItemFocus = React.useCallback((event) => {
        if (event.target !== event.currentTarget) {
            return;
        }
    }, []);

    const listItemCard = (session: SharingSessionBasicDto) => {
        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <Avatar name={session.recipientEmail}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <div className={styles.caption1}>{session.recipientEmail}</div>
                    <div className={styles.listCardItemRow}>
                        <div className={styles.body1Strong}>{session.sessionName}</div>
                        <div className={styles.caption2}>{formatDateWithOrdinal(session.createdDate)}</div>
                    </div>
                    <div>
                        <div className={styles.caption1}> {session.description} </div>
                    </div>
                </span>
            </section>
        </div>
    };

    const listItemCardSkeleton = () => {
        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <SkeletonItem shape="circle" size={36}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <SkeletonItem size={12} className={styles.skeletonRecipientEmail}/>
                    <div className={styles.listCardItemRow}>
                        <SkeletonItem size={20} className={styles.skeletonSessionName}/>
                        <SkeletonItem size={16} className={styles.skeletonCreatedDate}/>
                    </div>
                    <div>
                        <SkeletonItem size={16} className={styles.skeletonSessionDescription}/>
                    </div>
                </span>
            </section>
        </div>
    };

    return (
        <section className={styles.sharingSessionsListContainer}>
            <List
                className={styles.sharingSessionsListBody}
                selectionMode="single"
                navigationMode="items"
                selectedItems={selectedItems}
                onSelectionChange={handleSelectionChange}>

                {loadingSharingSessions && Array.from({length: 10}).map((_, index) => (
                    <ListItem
                        className={index === 2 ? styles.sharingSessionsListSelectedItem : ""}
                        key={index}
                        value={index.toString()}
                        data-value={index.toString()}
                        checkmark={null}
                    >
                        {listItemCardSkeleton()}
                    </ListItem>
                ))}

                {!loadingSharingSessions && sharingSessions.map((session: SharingSessionBasicDto) => (
                    <ListItem
                        className={selectedItems.includes(session.id) ? styles.sharingSessionsListSelectedItem : ""}
                        key={session.id}
                        value={session.id}
                        data-value={session.id}
                        onFocus={onListItemFocus}
                        checkmark={null}
                    >
                        {listItemCard(session)}
                    </ListItem>
                ))}
            </List>
            <div className={styles.sharingSessionsListHeader}>
                <Field className={styles.filterSearchField}>
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
                            <Divider title={"status"}/>
                            <MenuItem> Initiated</MenuItem>
                            <MenuItem> Started</MenuItem>
                            <MenuItem> Rejected</MenuItem>
                            <MenuItem> Ended</MenuItem>
                            <Divider title={"status"}/>
                            <MenuItem> Initiated by me </MenuItem>
                            <MenuItem> Requested by others </MenuItem>
                            <MenuItem> Participating in </MenuItem>
                        </MenuList>
                    </MenuPopover>
                </Menu>
                <Menu>
                    <MenuTrigger>
                        <Tooltip content="Sort descending" relationship={"description"}>
                            <Button icon={<SortDownIcon/>} appearance={"subtle"}/>
                        </Tooltip>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList>
                            <Divider title={"status"}/>
                            <MenuItem> Date created </MenuItem>
                            <MenuItem> Session name </MenuItem>
                        </MenuList>
                    </MenuPopover>
                </Menu>
            </div>
            <div className={styles.sharingSessionsListFooter}>
                <span>
                    {loadingSharingSessions && <Spinner size={"extra-small"}/>}
                    {!loadingSharingSessions && <>
                        Showing <strong> {sharingSessions.length} </strong> Sharing Sessions
                    </>}
                </span>
                <Button size={"small"} appearance={"primary"} disabled>View All</Button>
            </div>
        </section>
    );
};

export default SharingSessionList;