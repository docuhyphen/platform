import React from 'react';
import {mergeClasses} from '@fluentui/react-components';
import ExchangeDetailsLoading from '../exchange-details-loading/ExchangeDetailsLoading.tsx';
import ExchangeDetailsError from '../exchange-details-error/ExchangeDetailsError.tsx';
import ExchangeDetailsPane from '../exchange-details-pane/ExchangeDetailsPane.tsx';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';
import {useExchangeDetailsColumnStyles} from './ExchangeDetailsColumnStyles.tsx';

interface ExchangeDetailsColumnProps
{
    workspace: ExchangesWorkspace;
    showDetailsColumnFallbacks: boolean;
}

const ExchangeDetailsColumn: React.FC<ExchangeDetailsColumnProps> = ({workspace, showDetailsColumnFallbacks}) =>
{
    const styles = useExchangeDetailsColumnStyles();
    const {
        selectedExchangeId,
        detailsTransitionAnimationSuppressedForId,
        showInitialDetailsSkeleton,
        showSelectedDetailsSkeleton,
        hasDetailsLoadError,
        hasSelectedDetails,
        exchangeDetails,
        setRouteSelectionVersion,
    } = workspace;
    const shouldAnimateDetailsTransition =
        selectedExchangeId !== detailsTransitionAnimationSuppressedForId;

    return (
        <>
            {showDetailsColumnFallbacks && showInitialDetailsSkeleton && (
                <div id={"exchanges-details-transition-frame"}
                     className={styles.detailsTransitionFrame}>
                    <ExchangeDetailsLoading/>
                </div>
            )}

            {!!selectedExchangeId && (
                <div
                    id={`exchange-details-transition-${selectedExchangeId}`}
                    key={selectedExchangeId}
                    className={mergeClasses(
                        styles.detailsTransitionFrame,
                        shouldAnimateDetailsTransition && styles.detailsSlideInFromRight
                    )}
                >
                    {!hasDetailsLoadError && showSelectedDetailsSkeleton && <ExchangeDetailsLoading/>}

                    {hasDetailsLoadError && !hasSelectedDetails && (
                        <ExchangeDetailsError onRetry={() => setRouteSelectionVersion(version => version + 1)}/>
                    )}

                    {hasSelectedDetails && exchangeDetails &&
                        <ExchangeDetailsPane workspace={workspace}/>
                    }
                </div>
            )}
        </>
    );
};

export default ExchangeDetailsColumn;
