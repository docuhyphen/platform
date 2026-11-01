export const getExchangeCountLabel = (count: number, type: 'active' | 'archived') =>
{
    return `${count} ${type} ${count === 1 ? 'exchange' : 'exchanges'}`;
};
