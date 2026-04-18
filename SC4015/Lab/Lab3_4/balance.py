from smartcard.System import readers
from smartcard.util import toHexString

# MIFARE Ultralight commands
CMD_GET_UID = [0x00, 0x00, 0x00, 0x00, 0x00]

CMD_GET_PURSE_FILE = None

CMD_GET_TRANSACTION_LOG = None

# Function to send APDU commands to the card
def send_apdu(connection, apdu_cmd):
    data, sw1, sw2 = connection.transmit(apdu_cmd)
    response = toHexString(data)
    status_code = "SW1: {:02X}, SW2: {:02X}".format(sw1, sw2)
    return response, status_code

def main():
    # Get all available smart card readers
    card_readers = readers()

    if not card_readers:
        print("No smart card readers found.")
        return

    print("Available smart card readers:")
    for reader in card_readers:
        print(reader)

    # Select the reader you want to connect to....
    reader = card_readers[1]

    # Connect to the selected reader
    connection = reader.createConnection()
    connection.connect()

    # --- READ ---
    CMD_READ_CMD = [0x90, 0x32, 0x03, 0x00, 0x00]
    response, status_code = send_apdu(connection, CMD_READ_CMD)

    if status_code == "SW1: 90, SW2: 00":
        # Extract UID from the response
        uid = response[5:14]
        hex1 = uid.replace(" ","" )
        cents = int(hex1, 16)
        dollar = cents /100
        print("Balance:", dollar)
    else:
        print("Failed to retrieve UID.")

    # Disconnect from the reader
    connection.disconnect()

if __name__ == "__main__":
    main()
