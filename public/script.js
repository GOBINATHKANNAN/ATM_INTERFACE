// Global State
let currentPin = "";
let currentAccount = "";
let isSignUpMode = false;
let currentHistory = [];

// Elements
const loginScreen = document.getElementById('login-screen');
const dashboardScreen = document.getElementById('dashboard-screen');
const pinDots = document.querySelectorAll('.dot');
const accountInput = document.getElementById('account-input');
const toastContainer = document.getElementById('toast-container');
const authSubtitle = document.getElementById('auth-subtitle');
const toggleText = document.getElementById('toggle-auth-text');
const toggleBtn = document.getElementById('toggle-auth-btn');

function encryptPayload(text) {
    let shifted = "";
    for (let i = 0; i < text.length; i++) {
        shifted += String.fromCharCode(text.charCodeAt(i) + 1);
    }
    return btoa(shifted);
}

document.querySelectorAll('.key').forEach(key => {
    key.addEventListener('click', () => {
        if (key.classList.contains('clear')) {
            currentPin = "";
            updatePinDots();
        } else if (key.classList.contains('enter')) {
            attemptAuth();
        } else {
            if (currentPin.length < 4) {
                currentPin += key.innerText;
                updatePinDots();
            }
        }
    });
});

function updatePinDots() {
    pinDots.forEach((dot, index) => {
        if (index < currentPin.length) dot.classList.add('filled');
        else dot.classList.remove('filled');
    });
}

function toggleAuthMode() {
    isSignUpMode = !isSignUpMode;
    if (isSignUpMode) {
        authSubtitle.innerText = "Register New Account";
        toggleText.innerText = "Already have an account? ";
        toggleBtn.innerText = "Log In";
    } else {
        authSubtitle.innerText = "Secure Banking Portal";
        toggleText.innerText = "New user? ";
        toggleBtn.innerText = "Sign Up here";
    }
    currentPin = "";
    updatePinDots();
}

async function attemptAuth() {
    const acc = accountInput.value.trim();
    if (!acc) return showToast("Enter Account Number", "error");
    if (currentPin.length !== 4) return showToast("Enter 4-digit PIN", "error");

    const encryptedToken = encryptPayload(`${acc}:${currentPin}`);
    const endpoint = isSignUpMode ? "/api/signup" : "/api/login";

    try {
        const res = await fetch(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: `token=${encodeURIComponent(encryptedToken)}`
        });
        const data = await res.json();
        
        if (data.success) {
            currentAccount = acc;
            document.getElementById('logged-in-user').innerText = `Account: ${acc}`;
            loginScreen.classList.remove('active');
            dashboardScreen.classList.add('active');
            showToast(isSignUpMode ? "Registration Successful!" : "Login Successful", "success");
            fetchData();
        } else {
            showToast(data.message, "error");
            currentPin = "";
            updatePinDots();
        }
    } catch (error) { showToast("Server error", "error"); }
}

function logout() {
    currentAccount = "";
    currentPin = "";
    updatePinDots();
    dashboardScreen.classList.remove('active');
    loginScreen.classList.add('active');
    showToast("Logged out", "success");
}

async function fetchData() {
    if(!currentAccount) return;
    try {
        const res = await fetch(`/api/userinfo?account=${currentAccount}`);
        const data = await res.json();
        
        if (data.isFrozen) {
            document.getElementById('balance-display').innerText = "FROZEN";
            document.getElementById('balance-display').style.color = "var(--danger)";
            if (!document.getElementById('frozen-toast-shown')) {
                showToast("Account locked due to suspected fraud.", "error");
                let el = document.createElement('div');
                el.id = 'frozen-toast-shown';
                document.body.appendChild(el);
            }
        } else {
            document.getElementById('balance-display').innerText = "$" + data.balance.toLocaleString('en-US', {minimumFractionDigits: 2});
            document.getElementById('balance-display').style.color = "var(--text-dark)";
        }
        
        currentHistory = data.history;
        const hl = document.getElementById('history-list');
        hl.innerHTML = data.history.length === 0 ? '<div style="color:var(--text-muted);text-align:center;margin-top:20px;font-size:14px;">No transactions yet</div>' : '';
        data.history.forEach(tx => {
            const isDeposit = tx.type.toLowerCase().includes('deposit') || tx.type.toLowerCase().includes('received');
            const sign = isDeposit ? '+' : '-';
            const cls = isDeposit ? 'deposit' : 'withdraw';
            hl.innerHTML += `
                <li class="history-item">
                    <div class="history-info">
                        <span class="history-type">${tx.type}</span>
                        <span class="history-date">${tx.date}</span>
                    </div>
                    <span class="history-amount ${cls}">${sign}$${tx.amount.toLocaleString('en-US', {minimumFractionDigits: 2})}</span>
                </li>
            `;
        });

        const cl = document.getElementById('contacts-list');
        const cd = document.getElementById('contact-dropdown');
        cl.innerHTML = data.contacts.length === 0 ? '<span style="color:#94a3b8; font-size:12px;">No contacts added</span>' : '';
        cd.innerHTML = '<option value="">Select Contact</option>';
        data.contacts.forEach(c => {
            cl.innerHTML += `<div class="contact-chip">@${c}</div>`;
            cd.innerHTML += `<option value="${c}">${c}</option>`;
        });
        
    } catch (e) { console.error(e); }
}

function downloadStatement() {
    if (currentHistory.length === 0) return showToast("No transactions to download", "error");
    
    let csv = "Date,Type,Amount\n";
    currentHistory.forEach(tx => {
        csv += `"${tx.date}","${tx.type}","${tx.amount.toFixed(2)}"\n`;
    });
    
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.setAttribute('href', url);
    a.setAttribute('download', `GOB_Statement_${currentAccount}.csv`);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    showToast("Statement Downloaded!", "success");
}

async function addContact() {
    const contact = document.getElementById('new-contact-input').value.trim();
    if (!contact) return showToast("Enter an account to add", "error");

    try {
        const res = await fetch("/api/addcontact", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: `account=${currentAccount}&contact=${encodeURIComponent(contact)}`
        });
        const data = await res.json();
        if (data.success) {
            document.getElementById('new-contact-input').value = "";
            showToast("Contact added!", "success");
            fetchData();
        } else showToast(data.message, "error");
    } catch(e) { showToast("Server error", "error"); }
}

async function transact(type) {
    const amountInput = document.getElementById('amount-input');
    const amount = amountInput.value;
    
    if (!amount || amount <= 0) return showToast("Enter a valid amount", "error");

    let recipient = "";
    if (type === 'transfer') {
        const dd = document.getElementById('contact-dropdown').value;
        const man = document.getElementById('manual-recipient').value.trim();
        recipient = man || dd;
        if (!recipient) return showToast("Select or enter a recipient", "error");
    }

    try {
        let body = `account=${currentAccount}&type=${type}&amount=${amount}`;
        if (type === 'transfer') body += `&recipient=${encodeURIComponent(recipient)}`;

        const res = await fetch("/api/transaction", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body
        });
        const data = await res.json();
        
        if (data.success) {
            amountInput.value = "";
            document.getElementById('manual-recipient').value = "";
            document.getElementById('contact-dropdown').value = "";
            showToast("Transaction successful!", "success");
            fetchData(); 
        } else {
            showToast(data.message, "error");
        }
    } catch (error) { showToast("Server error", "error"); }
}

function showToast(msg, type="success") {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerText = msg;
    toastContainer.appendChild(toast);
    setTimeout(() => { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 3500);
}
  