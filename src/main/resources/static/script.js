const API_BASE = '/api';

async function fetchBalance() {
    try {
        const response = await fetch(`${API_BASE}/balance`);
        const data = await response.json();
        document.getElementById('balance-val').innerText = data.balance.toFixed(2);
    } catch (error) {
        showToast('Error fetching balance');
    }
}

async function fetchTransactions() {
    const listElement = document.getElementById('transaction-list');
    try {
        const response = await fetch(`${API_BASE}/transactions`);
        const transactions = await response.json();
        
        if (transactions.length === 0) {
            listElement.innerHTML = '<div class="loading">No transactions yet</div>';
            return;
        }

        listElement.innerHTML = transactions.map(tx => `
            <div class="transaction-item">
                <div class="tx-info">
                    <h4>${tx.type}</h4>
                    <p>${new Date(tx.timestamp).toLocaleString()}</p>
                </div>
                <div class="tx-amount ${tx.type.toLowerCase() === 'deposit' ? 'deposit' : 'withdraw'}">
                    ${tx.type.toLowerCase() === 'deposit' ? '+' : '-'}$${tx.amount.toFixed(2)}
                </div>
            </div>
        `).join('');
    } catch (error) {
        listElement.innerHTML = '<div class="loading">Error loading history</div>';
    }
}

async function handleTransaction(type) {
    const amountInput = document.getElementById('amount');
    const amount = parseFloat(amountInput.value);

    if (isNaN(amount) || amount <= 0) {
        showToast('Please enter a valid amount');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/${type}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ amount })
        });

        const data = await response.json();

        if (data.error) {
            showToast(data.error);
        } else {
            showToast(`${type.charAt(0).toUpperCase() + type.slice(1)} successful!`);
            amountInput.value = '';
            fetchBalance();
            fetchTransactions();
        }
    } catch (error) {
        showToast('Transaction failed. Check connection.');
    }
}

function showToast(message) {
    const toast = document.getElementById('toast');
    toast.innerText = message;
    toast.classList.add('show');
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Initial Load
window.onload = () => {
    fetchBalance();
    fetchTransactions();
};
