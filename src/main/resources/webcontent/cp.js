(function () {
    const useSampleOnFail = true;
    let xsrfToken = null;
    let currentInfo = null;
    let rewardsAlreadyAdded = [];
    let sampleInfo = {
        "rewards": [
            {
                "id": "spawn_cat",
                "name": "Spawn Cat",
                "description": "Spawn a cat!",
                "stringInput": true,
                "minimumCost": 5000
            },
            {
                "id": "spawn_wolf",
                "name": "Spawn Wolf",
                "description": "Spawn a wolf!",
                "stringInput": true,
                "minimumCost": 7500
            }
        ],
        "currentRewards": {
            "92af127c-7326-4483-a52b-b0da0be61c01": "spawn_cat"
        },
        "twitchRewards": [
            {
                "broadcaster_name": "torpedo09",
                "broadcaster_login": "torpedo09",
                "broadcaster_id": "274637212",
                "id": "92af127c-7326-4483-a52b-b0da0be61c01",
                "image": null,
                "background_color": "#00E5CB",
                "is_enabled": true,
                "cost": 50000,
                "title": "Spawn Cat",
                "prompt": "Spawn a cat!",
                "is_user_input_required": true,
                "max_per_stream_setting": {
                    "is_enabled": false,
                    "max_per_stream": 0
                },
                "max_per_user_per_stream_setting": {
                    "is_enabled": false,
                    "max_per_user_per_stream": 0
                },
                "global_cooldown_setting": {
                    "is_enabled": false,
                    "global_cooldown_seconds": 0
                },
                "is_paused": false,
                "is_in_stock": true,
                "default_image": {
                    "url_1x": "https://static-cdn.jtvnw.net/custom-reward-images/default-1.png",
                    "url_2x": "https://static-cdn.jtvnw.net/custom-reward-images/default-2.png",
                    "url_4x": "https://static-cdn.jtvnw.net/custom-reward-images/default-4.png"
                },
                "should_redemptions_skip_request_queue": false,
                "redemptions_redeemed_current_stream": null,
                "cooldown_expires_at": null
            }
        ],
        "xsrfToken": "1234"
    };

    let loadInfo = function () {
        return new Promise((resolve, reject) => {
            let xhr = new XMLHttpRequest();
            xhr.open("GET", "info.json");
            xhr.onreadystatechange = function () {
                if (xhr.readyState === XMLHttpRequest.DONE) {
                    if (xhr.status !== 200) {
                        if (useSampleOnFail) {
                            resolve(sampleInfo);
                        } else {
                            reject();
                        }
                    } else {
                        resolve(JSON.parse(xhr.responseText));
                    }
                }
            };
            xhr.send();
        });
    };

    let addReward = function (rewardType) {
        return new Promise((resolve, reject) => {
            let xhr = new XMLHttpRequest();
            xhr.open("POST", "addreward");
            xhr.onreadystatechange = function () {
                if (xhr.readyState === XMLHttpRequest.DONE) {
                    if (xhr.status !== 200) {
                        reject();
                    } else {
                        resolve(JSON.parse(xhr.responseText));
                    }
                }
            };
            xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            xhr.send("reward=" + encodeURIComponent(rewardType) + "&xsrfToken=" + xsrfToken);
        });
    };

    let isRewardAlreadyAdded = function(rewardType) {
        for (let i = 0; i < rewardsAlreadyAdded.length; i++) {
            if (rewardsAlreadyAdded[i] === rewardType) {
                return true;
            }
        }
        return false;
    };

    let reloadInfo = function() {
        loadInfo().then((info) => {
            currentInfo = info;
            xsrfToken = currentInfo.xsrfToken;
            rewardsAlreadyAdded = [];
            for (let i = 0; i < currentInfo.twitchRewards.length; i++) {
                let twitchReward = currentInfo.twitchRewards[i];
                let rewardType = currentInfo.currentRewards[twitchReward.id];
                rewardsAlreadyAdded.push(rewardType);
            }
            buildPage();
        }).catch(() => {
            currentInfo = null;
            buildPage();
        });
    };

    let buildPage = function() {
        while (document.body.firstChild) {
            document.body.removeChild(document.body.firstChild);
        }
        if (currentInfo == null) {
            document.body.appendChild(document.createTextNode("There was a problem loading this page."));
            return;
        }
        document.body.appendChild(document.createTextNode("Channel Point Rewards you can add"));
        let table = document.createElement("TABLE");
        document.body.appendChild(table);
        let tbody = document.createElement("TBODY");
        table.appendChild(tbody);
        let headTr = document.createElement("TR");
        tbody.appendChild(headTr);
        headTr.className = "head";
        let addHeaderTr = document.createElement("TD");
        addHeaderTr.appendChild(document.createTextNode("Add"));
        headTr.appendChild(addHeaderTr);
        let titleHeaderTr = document.createElement("TD");
        titleHeaderTr.appendChild(document.createTextNode("Title"));
        headTr.appendChild(titleHeaderTr);
        let minimumCostHeadTr = document.createElement("TD");
        minimumCostHeadTr.appendChild(document.createTextNode("Min Cost"));
        headTr.appendChild(minimumCostHeadTr);
        let descriptionHeadTr = document.createElement("TD");
        descriptionHeadTr.appendChild(document.createTextNode("Description"));
        headTr.appendChild(descriptionHeadTr);
        for (let i = 0; i < currentInfo.rewards.length; i++) {
            let reward = currentInfo.rewards[i];
            let tr = document.createElement("TR");
            tbody.appendChild(tr);
            tr.className = "alt" + (i % 2 === 0 ? "1" : "2");
            let addButtonTd = document.createElement("TD");
            tr.appendChild(addButtonTd);
            if (isRewardAlreadyAdded(reward.id)) {
                addButtonTd.appendChild(document.createTextNode("Added"));
            } else {
                let addButton = document.createElement("INPUT");
                addButtonTd.appendChild(addButton);
                addButton.type = "button";
                addButton.value = "Add";
                addButton.addEventListener("click", () => {
                    addButton.disabled = true;
                    addReward(reward.id).then(() => {
                        addButtonTd.removeChild(addButton);
                        addButtonTd.appendChild(document.createTextNode("Added"));
                    }).catch(() => {
                        addButton.disabled = false;
                        addButton.value = "Failed - Retry?";
                    });
                });
            }
            let titleTd = document.createElement("TD");
            tr.appendChild(titleTd);
            titleTd.appendChild(document.createTextNode(reward.name));
            let minCostTd = document.createElement("TD");
            tr.appendChild(minCostTd);
            minCostTd.appendChild(document.createTextNode(reward.minimumCost));
            let descriptionTd = document.createElement("TD");
            tr.appendChild(descriptionTd);
            descriptionTd.appendChild(document.createTextNode(reward.description));
        }
        document.body.appendChild(document.createTextNode("Item prices listed above are the minimum allowed cost."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("To edit prices or remove rewards, "));
        let linkToTwitch = document.createElement("A");
        linkToTwitch.innerText = "visit your Twitch dashboard.";
        linkToTwitch.href = "https://dashboard.twitch.tv/viewer-rewards/channel-points/rewards";
        linkToTwitch.target = "_blank";
        document.body.appendChild(linkToTwitch);
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("You can set a higher price if you want and choose your own cooldown in the Twitch dashboard."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("Changing the price to be below the minimum cost will cause the redemption to be automatically rejected."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("IMPORTANT: Redemptions are automatically marked as complete once the action has been performed in the server and cannot be refunded."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("Actions that are not completed for any reason are automatically rejected."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("If you are not currently playing on the server, redemptions will not be completed and will be automatically rejected."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("Redeeming your own redemptions are not permitted unless doing it to fix a redemption that failed."));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createElement("BR"));
        document.body.appendChild(document.createTextNode("If you find a pending redemption in your request queue, you should reject it."));
    };

    window.addEventListener("load", () => {
        reloadInfo();
    });

})();
